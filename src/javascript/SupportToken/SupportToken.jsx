import React, {useState} from 'react';
import {useLazyQuery, useMutation} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography} from '@jahia/moonstone';
import styles from './SupportToken.scss';
import {CLEAR_ALL_TOKENS, CREATE_TOKEN, LIST_TOKENS} from './SupportToken.gql';

const DEFAULT_FORM = {
    recipient: '',
    description: '',
    expiration: 60
};

export const SupportTokenAdmin = () => {
    const {t} = useTranslation('support-token-authentication-valve');

    const [username, setUsername] = useState('');
    const [siteKey, setSiteKey] = useState('');
    const [form, setForm] = useState(DEFAULT_FORM);
    const [searchedUser, setSearchedUser] = useState(null);
    const [userNotFound, setUserNotFound] = useState(false);
    const [actionStatus, setActionStatus] = useState(null);
    const [generatedToken, setGeneratedToken] = useState(null);
    const [copied, setCopied] = useState(false);
    const [recipientError, setRecipientError] = useState('');

    const [listTokens, {loading: searching, data: tokensData}] = useLazyQuery(LIST_TOKENS, {
        fetchPolicy: 'network-only'
    });

    const [createToken, {loading: creating}] = useMutation(CREATE_TOKEN);
    const [clearAllTokens, {loading: clearing}] = useMutation(CLEAR_ALL_TOKENS);

    const tokens = tokensData?.supportTokenListTokens ?? null;

    const handleSearch = async () => {
        if (!username.trim()) {
            return;
        }
        setActionStatus(null);
        setGeneratedToken(null);
        setUserNotFound(false);
        setSearchedUser(null);
        setForm(DEFAULT_FORM);

        const result = await listTokens({
            variables: {username: username.trim(), siteKey: siteKey.trim() || null}
        });
        if (result.data?.supportTokenListTokens === null) {
            setUserNotFound(true);
        } else {
            setSearchedUser(username.trim());
        }
    };

    const handleFormChange = field => e => {
        setActionStatus(null);
        setGeneratedToken(null);
        const value = field === 'expiration' ? Number.parseInt(e.target.value, 10) || 0 : e.target.value;
        setForm(prev => ({...prev, [field]: value}));
        if (field === 'recipient') {
            setRecipientError('');
        }
    };

    const handleCreate = async () => {
        if (!form.recipient.trim()) {
            setRecipientError(t('label.recipient') + ' is required');
            return;
        }
        setActionStatus(null);
        setGeneratedToken(null);
        try {
            const result = await createToken({
                variables: {
                    username: searchedUser,
                    siteKey: siteKey.trim() || null,
                    recipient: form.recipient.trim(),
                    description: form.description.trim() || null,
                    expiration: form.expiration
                }
            });
            const token = result.data?.supportTokenCreate;
            if (token) {
                setGeneratedToken(token);
                setActionStatus('createSuccess');
                setForm(DEFAULT_FORM);
                listTokens({variables: {username: searchedUser, siteKey: siteKey.trim() || null}});
            } else {
                setActionStatus('createError');
            }
        } catch (err) {
            console.error('Failed to create token:', err);
            setActionStatus('createError');
        }
    };

    const handleClear = async () => {
        setActionStatus(null);
        setGeneratedToken(null);
        try {
            const result = await clearAllTokens({
                variables: {username: searchedUser, siteKey: siteKey.trim() || null}
            });
            if (result.data?.supportTokenClearAll) {
                setActionStatus('clearSuccess');
                listTokens({variables: {username: searchedUser, siteKey: siteKey.trim() || null}});
            } else {
                setActionStatus('clearError');
            }
        } catch (err) {
            console.error('Failed to clear tokens:', err);
            setActionStatus('clearError');
        }
    };

    const handleCopy = () => {
        if (generatedToken) {
            navigator.clipboard.writeText(generatedToken).then(() => {
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
            });
        }
    };

    const busy = creating || clearing;

    return (
        <div className={styles.st_container}>
            <div className={styles.st_header}>
                <h2>{t('label.title')}</h2>
            </div>
            <div className={styles.st_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            {/* User search */}
            <div className={styles.st_searchRow}>
                <div className={styles.st_fieldGroup}>
                    <label className={styles.st_label} htmlFor="st-username">{t('label.username')}</label>
                    <input
                        id="st-username"
                        className={styles.st_input}
                        value={username}
                        placeholder={t('label.usernamePlaceholder')}
                        onChange={e => setUsername(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handleSearch()}
                    />
                </div>
                <div className={styles.st_fieldGroup}>
                    <label className={styles.st_label} htmlFor="st-sitekey">{t('label.siteKey')}</label>
                    <input
                        id="st-sitekey"
                        className={styles.st_input}
                        value={siteKey}
                        placeholder={t('label.siteKeyPlaceholder')}
                        onChange={e => setSiteKey(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handleSearch()}
                    />
                </div>
                <Button
                    id="st-search"
                    label={t('label.search')}
                    variant="primary"
                    isDisabled={searching || !username.trim()}
                    onClick={handleSearch}
                />
                {searching && <Loader size="small"/>}
            </div>

            {userNotFound && (
                <div className={`${styles.st_alert} ${styles['st_alert--error']}`}>
                    {t('label.userNotFound')}
                </div>
            )}

            {searchedUser && (
                <>
                    {/* Feedback alerts */}
                    {actionStatus === 'createSuccess' && (
                        <div className={`${styles.st_alert} ${styles['st_alert--success']}`}>
                            {t('label.createSuccess')}
                        </div>
                    )}
                    {actionStatus === 'createError' && (
                        <div className={`${styles.st_alert} ${styles['st_alert--error']}`}>
                            {t('label.createError')}
                        </div>
                    )}
                    {actionStatus === 'clearSuccess' && (
                        <div className={`${styles.st_alert} ${styles['st_alert--success']}`}>
                            {t('label.clearSuccess')}
                        </div>
                    )}
                    {actionStatus === 'clearError' && (
                        <div className={`${styles.st_alert} ${styles['st_alert--error']}`}>
                            {t('label.clearError')}
                        </div>
                    )}

                    {/* Generated token display */}
                    {generatedToken && (
                        <div className={styles.st_tokenBox}>
                            <span className={styles.st_tokenValue}>{generatedToken}</span>
                            <Button
                                label={copied ? t('label.tokenCopied') : t('label.copyToken')}
                                variant="ghost"
                                onClick={handleCopy}
                            />
                        </div>
                    )}

                    {/* Existing tokens table */}
                    <div className={styles.st_section}>
                        <p className={styles.st_sectionTitle}>{t('label.tokensTitle')}</p>
                        {tokens === null || tokens === undefined ? (
                            <div className={styles.st_loading}><Loader size="small"/></div>
                        ) : tokens.length === 0 ? (
                            <span className={styles.st_emptyMsg}>{t('label.noTokens')}</span>
                        ) : (
                            <table className={styles.st_table}>
                                <thead>
                                    <tr>
                                        <th>{t('label.tokenCreatedDate')}</th>
                                        <th>{t('label.tokenRecipient')}</th>
                                        <th>{t('label.tokenDescription')}</th>
                                        <th>{t('label.tokenExpiration')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {tokens.map((token, idx) => (
                                        <tr key={idx}>
                                            <td>{token.createdDate}</td>
                                            <td>{token.recipient}</td>
                                            <td>{token.description}</td>
                                            <td>{token.expiration}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        )}
                    </div>

                    {/* Create token form */}
                    <div className={styles.st_section}>
                        <p className={styles.st_sectionTitle}>{t('label.createSection')}</p>
                        <div className={styles.st_form}>
                            <div className={styles.st_formRow}>
                                <div className={styles.st_fieldGroup}>
                                    <label className={styles.st_label} htmlFor="st-recipient">{t('label.recipient')}</label>
                                    <input
                                        id="st-recipient"
                                        className={`${styles.st_input}${recipientError ? ` ${styles['st_input--error']}` : ''}`}
                                        value={form.recipient}
                                        placeholder={t('label.recipientPlaceholder')}
                                        onChange={handleFormChange('recipient')}
                                    />
                                    {recipientError && <span className={styles.st_errorMsg}>{recipientError}</span>}
                                </div>
                                <div className={styles.st_fieldGroup}>
                                    <label className={styles.st_label} htmlFor="st-expiration">{t('label.expiration')}</label>
                                    <input
                                        id="st-expiration"
                                        type="number"
                                        min="1"
                                        className={styles.st_numberInput}
                                        value={form.expiration}
                                        onChange={handleFormChange('expiration')}
                                    />
                                </div>
                            </div>
                            <div className={styles.st_fieldGroup}>
                                <label className={styles.st_label} htmlFor="st-description">{t('label.descriptionField')}</label>
                                <input
                                    id="st-description"
                                    className={styles.st_input}
                                    value={form.description}
                                    placeholder={t('label.descriptionPlaceholder')}
                                    onChange={handleFormChange('description')}
                                />
                            </div>
                        </div>
                        <div className={styles.st_actions}>
                            <Button
                                label={t('label.createToken')}
                                variant="primary"
                                isDisabled={busy}
                                onClick={handleCreate}
                            />
                            <Button
                                label={t('label.clearAllTokens')}
                                variant="destructive"
                                isDisabled={busy || !tokens || tokens.length === 0}
                                onClick={handleClear}
                            />
                        </div>
                    </div>
                </>
            )}
        </div>
    );
};

export default SupportTokenAdmin;
