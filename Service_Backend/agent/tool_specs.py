"""OpenAI-format tool specifications passed to DeepSeek.

DeepSeek's chat.completions API is OpenAI-compatible, so we use the same
schema. Each spec's `name` must match a method on AgentToolkit.

Author: Cai Peilin
"""

TOOL_SPECS = [
    {
        "type": "function",
        "function": {
            "name": "get_recent_wellness_records",
            "description": (
                "Retrieve the user's wellness records (sleep hours, exercise "
                "activity and duration) for the past N days. Must be called "
                "first; other analysis tools depend on the records it loads."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "days": {
                        "type": "integer",
                        "description": "Number of days back to fetch (1-30).",
                        "minimum": 1,
                        "maximum": 30,
                    }
                },
                "required": ["days"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "analyze_sleep_pattern",
            "description": (
                "Compute sleep statistics from the loaded records: mean hours, "
                "standard deviation, linear trend, and count of nights below "
                "6 hours. Requires get_recent_wellness_records to be called first."
            ),
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "analyze_exercise_pattern",
            "description": (
                "Compute exercise statistics: total minutes, active days, "
                "most common activity, and gap to WHO weekly guideline of "
                "150 minutes. Requires records to be loaded first."
            ),
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "save_recommendation",
            "description": (
                "Persist the final recommendation text to the database. "
                "Call this exactly once, as your last action."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "content": {
                        "type": "string",
                        "description": "The recommendation text shown to the user.",
                    }
                },
                "required": ["content"],
            },
        },
    },
]