// Marks the current page in the navigation bar.
document.addEventListener("DOMContentLoaded", () => {
  for (const link of document.querySelectorAll("nav a")) {
    if (link.pathname === window.location.pathname) {
      link.classList.add("current");
      link.setAttribute("aria-current", "page");
    }
  }
});
