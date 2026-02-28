import { useState } from "react"; // React hook for managing component state
import { login, register } from "../services/auth";// Import authentication functions from our auth service
import "../styles/login.css";

function LoginPage({ onLoginSuccess }) {
  // Track whether we're in login or signup mode
  const [isSignup, setIsSignup] = useState(false);
  
  // Form fields
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState(null);

  // Handle login form submission
  function handleLogin(e) {
    e.preventDefault();
    setError(null);

    // Validate inputs
    if (!email || !password) {
      setError("Please enter email and password");
      return;
    }

    // Try to log in
    const result = login(email, password);

    if (result.success) {
      onLoginSuccess();
    } else {
      setError(result.error);
    }
  }

  // Handle signup form submission
  function handleSignup(e) {
    e.preventDefault();
    setError(null);

    // Validate inputs
    if (!name || !email || !password) {
      setError("Please fill in all fields");
      return;
    }

    if (password.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }

    // Try to create account
    const result = register(email, password, name);

    if (result.success) {
      onLoginSuccess();
    } else {
      setError(result.error);
    }
  }

  // Toggle between login and signup
  function toggleMode() {
    setIsSignup(!isSignup);
    setError(null);
    setEmail("");
    setPassword("");
    setName("");
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <h1 className="login-logo">RouteSense</h1>
        <h2>{isSignup ? "Create Account" : "Log In"}</h2>

        <form onSubmit={isSignup ? handleSignup : handleLogin}>
          {/* Show name field only for signup */}
          {isSignup && (
            <input
              type="text"
              placeholder="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          )}

          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          {error && <div className="error">{error}</div>}

          <button type="submit">
            {isSignup ? "Create Account" : "Log In"}
          </button>
        </form>

        {/* Toggle between login and signup */}
        <p style={{ marginTop: "16px", fontSize: "14px", color: "#666" }}>
          {isSignup ? "Already have an account? " : "Don't have an account? "}
          <span
            onClick={toggleMode}
            style={{ color: "#2f80ed", cursor: "pointer", fontWeight: "500" }}
          >
            {isSignup ? "Log in" : "Sign up"}
          </span>
        </p>
      </div>
    </div>
  );
}

export default LoginPage;
