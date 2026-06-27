"""Agentic loop: orchestrates DeepSeek function calling against the toolkit.

The executor is the only place that knows about the LLM. Tools know
nothing about the loop; the router knows nothing about prompts. This
separation keeps each layer independently testable and lets us swap
DeepSeek for another OpenAI-compatible provider by changing one file.

Author: Cai Peilin
"""
import json
import os
import logging
from openai import OpenAI

from agent.prompts import SYSTEM_PROMPT
from agent.tool_specs import TOOL_SPECS
from agent.tools import AgentToolkit


logger = logging.getLogger(__name__)

# Cap iterations so a misbehaving model cannot burn the API budget.
# Empirically 5 is enough: load records, two analyses, save = 4 calls.
MAX_ITERATIONS = 5

# Trigger phrase the agent emits when it has finished and saved.
# Used as a soft fallback if the model forgets to stop after save.
STOP_AFTER_TOOL = "save_recommendation"


class AgentError(Exception):
    """Raised when the agent loop cannot produce a valid recommendation."""


def _build_client() -> OpenAI:
    return OpenAI(
        base_url="https://api.deepseek.com",
        api_key=os.getenv("DEEPSEEK_API_KEY"),
    )


def run_agent(db, user_id: int) -> dict:
    """Execute the agentic loop for one user and return the saved result.

    Returns a dict matching agent.schemas.RecommendResponse.
    Raises AgentError if the loop terminates without persisting.
    """
    client = _build_client()
    toolkit = AgentToolkit(db=db, user_id=user_id)

    messages: list[dict] = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "Please generate today's wellness recommendation for me."},
    ]

    saved_id: int | None = None

    for step in range(1, MAX_ITERATIONS + 1):
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=messages,
            tools=TOOL_SPECS,
            tool_choice="auto",
            temperature=0.3,
        )
        msg = response.choices[0].message

        # Model finished without further tool calls.
        if not msg.tool_calls:
            if saved_id is None:
                # Model produced final text but never called save_recommendation.
                # Treat as a protocol violation rather than silently dropping.
                raise AgentError(
                    f"Agent terminated at step {step} without saving. "
                    f"Last message: {msg.content!r}"
                )
            break

        # Append the assistant turn verbatim so the next request keeps context.
        messages.append({
            "role": "assistant",
            "content": msg.content or "",
            "tool_calls": [
                {
                    "id": tc.id,
                    "type": "function",
                    "function": {"name": tc.function.name, "arguments": tc.function.arguments},
                }
                for tc in msg.tool_calls
            ],
        })

        # Execute every tool the model requested in this turn.
        for tc in msg.tool_calls:
            name = tc.function.name
            try:
                args = json.loads(tc.function.arguments or "{}")
            except json.JSONDecodeError:
                args = {}

            result = _dispatch(toolkit, name, args)
            if name == STOP_AFTER_TOOL and "saved_id" in result:
                saved_id = result["saved_id"]

            messages.append({
                "role": "tool",
                "tool_call_id": tc.id,
                "name": name,
                "content": json.dumps(result, default=str),
            })

        # Early exit: once we've persisted, no need to keep looping even if
        # the model could in principle keep going.
        if saved_id is not None:
            break
    else:
        # Loop exhausted without break — hit MAX_ITERATIONS.
        raise AgentError(
            f"Agent exceeded {MAX_ITERATIONS} iterations without saving."
        )

    if saved_id is None:
        raise AgentError("Agent loop exited without producing a saved_id.")

    # Recover the final recommendation text from the trace by reading what
    # was passed to save_recommendation. This avoids re-querying the DB.
    content = _extract_saved_content(messages)
    iterations = sum(1 for t in toolkit.trace if t["name"] != STOP_AFTER_TOOL)

    return {
        "recommendation": content,
        "evidence": toolkit.trace,
        "iterations": iterations,
        "saved_id": saved_id,
    }


def _dispatch(toolkit: AgentToolkit, name: str, args: dict) -> dict:
    """Route a tool call by name to the matching method on the toolkit."""
    method = getattr(toolkit, name, None)
    if method is None or not callable(method):
        return {"error": f"Unknown tool: {name}"}
    try:
        return method(**args)
    except TypeError as e:
        # Wrong argument shape from the model — return as data, not a raise,
        # so the model gets a chance to correct itself in the next turn.
        logger.warning("Tool %s rejected args %s: %s", name, args, e)
        return {"error": f"Invalid arguments for {name}: {e}"}


def _extract_saved_content(messages: list[dict]) -> str:
    """Find the content argument passed to the final save_recommendation call."""
    for m in reversed(messages):
        if m.get("role") != "assistant":
            continue
        for tc in m.get("tool_calls", []) or []:
            if tc["function"]["name"] == STOP_AFTER_TOOL:
                try:
                    return json.loads(tc["function"]["arguments"]).get("content", "")
                except json.JSONDecodeError:
                    return ""
    return ""