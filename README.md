# Support Token Authentication Valve

The purpose of this module is to create a temporary token for a user that will be used by a Support Team to login.
This way, it's not needed to disclose the real password (when it's known).

## Installation

- In Jahia, go to "Administration --> Server settings --> System components --> Modules"
- Upload the JAR **support-token-authentication-valve-X.X.X.jar**
- Check that the module is started
- Make sure the Mail server is configured and activated in the administration

## How to use
### In the administration mode

- Go to the items "User", either in the server settings or in the site settings
- Look for the user for which the token needs to be generated then click on it
  - Specify the recipient who will receive the token
  - Explain the purpose of this token (bug, ticket, delivery, etc)
  - Set the expiration in minutes
  - Click on the button "Add"
- An email will be sent to the recipient with the token and these information. The same email, but without the token, will be sent to the recipient of the notifications for this Jahia server.

### With Karaf commands
#### <a name="support-token:create"></a>support-token:create
Create a token for a user

**Options:**

Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -s | --site-key | | null | Site key
 -u | --username |x| null | Username
 -r | --recipient |x| null | Recipient of the token
 -d | --description | | Access for Jahia Support | Description
 -e | --expiration | | 60 | Expiration (in minutes)


**Example:**

    support-token:create -u root -r support@jahia.com 

#### <a name="support-token:list"></a>support-token:list
List tokens for a user

**Options:**

Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -s | --site-key | | null | Site key
 -u | --username |x| null | Username


**Example:**

    support-token:list -u root

#### <a name="support-token:create"></a>support-token:clear
Clear all tokens of a user

**Options:**

Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -s | --site-key | | null | Site key
 -u | --username |x| null | Username


**Example:**

    support-token:clear -u root 
