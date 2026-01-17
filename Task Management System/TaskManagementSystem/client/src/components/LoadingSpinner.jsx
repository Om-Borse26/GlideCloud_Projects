import { useEffect, useState } from "react";
import "./LoadingSpinner.css";

export default function LoadingSpinner({
  fullScreen = false,
  message = "Loading...",
}) {
  const [show, setShow] = useState(false);

  useEffect(() => {
    // Delay showing spinner to avoid flash for fast loads
    const timer = setTimeout(() => setShow(true), 150);
    return () => clearTimeout(timer);
  }, []);

  if (!show) return null;

  if (fullScreen) {
    return (
      <div className="loadingOverlay">
        <div className="loadingSpinnerWrap">
          <div className="spinner">
            <div className="spinnerRing"></div>
            <div className="spinnerRing"></div>
            <div className="spinnerRing"></div>
          </div>
          {message && <p className="loadingMessage">{message}</p>}
        </div>
      </div>
    );
  }

  return (
    <div className="loadingInline">
      <div className="spinner">
        <div className="spinnerRing"></div>
        <div className="spinnerRing"></div>
        <div className="spinnerRing"></div>
      </div>
      {message && <span className="loadingMessage">{message}</span>}
    </div>
  );
}
