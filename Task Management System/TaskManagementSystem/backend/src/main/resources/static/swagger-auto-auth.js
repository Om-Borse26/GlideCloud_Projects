/**
 * Custom Swagger UI script to automatically set the Bearer token after a successful login.
 */
window.onload = function() {
  const ui = window.ui;
  
  // Save the original execute function
  const originalExecute = ui.getSystem().specActions.execute;

  // Override execute to intercept responses
  ui.getSystem().specActions.execute = function(req) {
    // We only care about the login endpoint
    if (req.url.includes('/api/auth/login') && req.method === 'POST') {
      const originalOnComplete = req.on.response;
      
      req.on.response = function(response) {
        if (response.status === 200) {
          try {
            // Parse the response body
            const body = JSON.parse(response.data);
            const token = body.token;
            
            if (token) {
              // Set the Authorization header for Swagger
              // This format matches what Swagger UI expects for Bearer auth
              const auth = {
                "bearerAuth": {
                  "name": "bearerAuth",
                  "schema": {
                    "type": "http",
                    "in": "header",
                    "scheme": "bearer",
                    "bearerFormat": "JWT"
                  },
                  "value": token 
                }
              };
              
              ui.authActions.authorize(auth);
              
              console.log("Auto-authorized with new token!");
              alert("Login successful! Token has been automatically applied. You can now use other endpoints.");
            }
          } catch (e) {
            console.error("Failed to parse login response for auto-auth", e);
          }
        }
        
        // Call the original completion handler
        if (originalOnComplete) {
          originalOnComplete(response);
        }
      };
    }
    
    return originalExecute(req);
  };
};
