document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-confirm]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      const message = form.getAttribute("data-confirm") || "Are you sure?";
      if (!window.confirm(message)) {
        event.preventDefault();
      }
    });
  });

  document.querySelectorAll("[data-loading-form]").forEach((form) => {
    form.addEventListener("submit", () => {
      const targetId = form.getAttribute("data-loading-target");
      if (targetId) {
        const target = document.getElementById(targetId);
        if (target) {
          target.hidden = false;
        }
      }
      form.querySelectorAll("button").forEach((button) => {
        button.disabled = true;
      });
    });
  });

  const chatWindow = document.getElementById("chatWindow");
  if (chatWindow) {
    chatWindow.scrollTop = chatWindow.scrollHeight;
  }
});
