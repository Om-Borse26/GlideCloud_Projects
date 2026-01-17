import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./index.css";
import App from "./App.jsx";
import { ThemeProvider } from "./context/ThemeContext";
import { FocusTimerProvider } from "./context/FocusTimerContext";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <ThemeProvider>
      <FocusTimerProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </FocusTimerProvider>
    </ThemeProvider>
  </StrictMode>
);
