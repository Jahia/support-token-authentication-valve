# support-token-authentication-valve

The purpose of this module is to create a temporary token for a user that will be used by a Support Team to login.
This way, it's not needed to disclose the real password (when it's known).

# Installation

- In Jahia, go to "Administration --> Server settings --> System components --> Modules"
- Upload the JAR **support-token-authentication-valve-X.X.X.jar**
- Check that the module is started
- Make sure the Mail server is configured and activated in the administration

# Use

- Go to the items "User", either in the server settings or in the site settings
- Look for the user for which the token needs to be generated then click on it
  - Specify the recipient who will receive the token
  - Explain the purpose of this token (bug, ticket, delivery, etc)
  - Set the expiration in minutes
  - Click on the button "Add"
- An email will be sent to the recipient with the token and these information. The same email, but without the token, will be sent to the recipient of the notifications for this Jahia server.

test cla
