import {gql} from '@apollo/client';

export const LIST_TOKENS = gql`
    query SupportTokenListTokens($username: String!, $siteKey: String) {
        supportTokenListTokens(username: $username, siteKey: $siteKey) {
            createdDate
            recipient
            expiration
            description
        }
    }
`;

export const CREATE_TOKEN = gql`
    mutation SupportTokenCreate($username: String!, $siteKey: String, $recipient: String!, $description: String, $expiration: Long) {
        supportTokenCreate(username: $username, siteKey: $siteKey, recipient: $recipient, description: $description, expiration: $expiration)
    }
`;

export const CLEAR_ALL_TOKENS = gql`
    mutation SupportTokenClearAll($username: String!, $siteKey: String) {
        supportTokenClearAll(username: $username, siteKey: $siteKey)
    }
`;
