const AUTH_KEY = "routesense_auth";
const USERS_KEY = "routesense_users";

// Get all registered users from storage
function getUsers() {
  const data = localStorage.getItem(USERS_KEY);
  return data ? JSON.parse(data) : [];
}

// Save users to storage
function saveUsers(users) {
  localStorage.setItem(USERS_KEY, JSON.stringify(users));
}

// Create a new account
export function register(email, password, name) {
  // Get existing users
  const users = getUsers();
  
  // Check if email already exists
  const existingUser = users.find(user => user.email === email);
  if (existingUser) {
    return { success: false, error: "Email already registered" };
  }
  
  // Create new user
  const newUser = {
    id: Date.now(), // Simple unique ID
    email,
    password, // In real app, this would be hashed
    name,
    createdAt: new Date().toISOString()
  };
  
  // Add to users list and save
  users.push(newUser);
  saveUsers(users);
  
  // Auto-login the new user
  localStorage.setItem(AUTH_KEY, JSON.stringify({ email, name }));
  
  return { success: true };
}

// Log in with existing account
export function login(email, password) {
  // Get all users
  const users = getUsers();
  
  // Find matching user
  const user = users.find(u => u.email === email && u.password === password);
  
  if (user) {
    // Save login session
    localStorage.setItem(AUTH_KEY, JSON.stringify({ email: user.email, name: user.name }));
    return { success: true };
  }
  
  return { success: false, error: "Invalid email or password" };
}

// Remove session
export function logout() {
  localStorage.removeItem(AUTH_KEY);
}

// Check if user is logged in
export function getStoredAuth() {
  return localStorage.getItem(AUTH_KEY);
}
