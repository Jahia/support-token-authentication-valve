import React, {useEffect, useRef, useState} from 'react';
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

const formatDate = dateStr => {
    try {
        return new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(dateStr));
    } catch {
        return dateStr;
    }
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
    const [showClearConfirm, setShowClearConfirm] = useState(false);

    const recipientRef = useRef(null);
    const tokenBoxRef = useRef(null);
    const clearAllBtnRef = useRef(null);
    const confirmBtnRef = useRef(null);
    const cancelBtnRef = useRef(null);

    const [listTokens, {loading: searching, data: tokensData}] = useLazyQuery(LIST_TOKENS, {
        fetchPolicy: 'network-only'
    });

    const [createToken, {loading: creating}] = useMutation(CREATE_TOKEN);
    const [clearAllTokens, {loading: clearing}] = useMutation(CLEAR_ALL_TOKENS);

    const tokens = tokensData?.supportTokenListTokens ?? null;

    useEffect(() => {
        const prev = document.title;
        document.title = `${t('label.title')} — Jahia Administration`;
        return () => {
            document.title = prev;
        };
    }, [t]);

    useEffect(() => {
        if (showClearConfirm && confirmBtnRef.current) {
            confirmBtnRef.current.focus();
        }
    }, [showClearConfirm]);

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
            setRecipientError(t('label.recipientRequired'));
            setTimeout(() => recipientRef.current?.focus(), 50);
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
                setTimeout(() => tokenBoxRef.current?.focus(), 50);
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

    const handleClearRequest = () => setShowClearConfirm(true);

    const handleClearConfirm = () => {
        setShowClearConfirm(false);
        setTimeout(() => clearAllBtnRef.current?.querySelector('button')?.focus(), 50);
        handleClear();
    };

    const handleClearCancel = () => {
        setShowClearConfirm(false);
        setTimeout(() => clearAllBtnRef.current?.querySelector('button')?.focus(), 50);
    };

    const handleDialogKeyDown = e => {
        if (e.key === 'Escape') {
            handleClearCancel();
        } else if (e.key === 'Tab') {
            const focusable = [confirmBtnRef.current, cancelBtnRef.current].filter(Boolean);
            if (focusable.length < 2) {
                return;
            }

            if (e.shiftKey && document.activeElement === focusable[0]) {
                e.preventDefault();
                focusable[focusable.length - 1].focus();
            } else if (!e.shiftKey && document.activeElement === focusable[focusable.length - 1]) {
                e.preventDefault();
                focusable[0].focus();
            }
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

    // Derived live region content
    const srPoliteMsg = copied ? t('label.tokenCopied') :
        actionStatus === 'createSuccess' ? t('label.createSuccess') :
        actionStatus === 'clearSuccess' ? t('label.clearSuccess') :
        searching ? t('label.searching') : '';

    const srAssertiveMsg = userNotFound ? t('label.userNotFound') :
        actionStatus === 'createError' ? t('label.createError') :
        actionStatus === 'clearError' ? t('label.clearError') :
        generatedToken ? t('label.generatedToken') :
        recipientError || '';

    return (
        <div className={styles.st_container}>
            <div className={styles.st_header}>
                <h2>{t('label.title')}</h2>
            </div>
            <div className={styles.st_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            {/* Persistent live regions — always in DOM so AT registers them before content appears */}
            <div
                role="status"
                aria-live="polite"
                aria-atomic="true"
                className={styles.st_sr_only}
            >
                {srPoliteMsg}
            </div>
            <div
                role="alert"
                aria-live="assertive"
                aria-atomic="true"
                className={styles.st_sr_only}
            >
                {srAssertiveMsg}
            </div>

            {/* User search */}
            <div className={styles.st_searchRow}>
                <div className={styles.st_fieldGroup}>
                    <label className={styles.st_label} htmlFor="st-username">{t('label.username')}</label>
                    <input
                        id="st-username"
                        className={styles.st_input}
                        value={username}
                        required
                        aria-required="true"
                        autoComplete="username"
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
                        autoComplete="off"
                        placeholder={t('label.siteKeyPlaceholder')}
                        onChange={e => setSiteKey(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handleSearch()}
                    />
                </div>
                <Button
                    id="st-search"
                    type="button"
                    label={t('label.search')}
                    variant="primary"
                    isDisabled={searching || !username.trim()}
                    onClick={handleSearch}
                />
                {searching && <Loader size="small" aria-hidden="true"/>}
            </div>

            {userNotFound && (
                <div aria-hidden="true" className={`${styles.st_alert} ${styles['st_alert--error']}`}>
                    {t('label.userNotFound')}
                </div>
            )}

            {searchedUser && (
                <>
                    {/* Feedback alerts */}
                    {actionStatus === 'createSuccess' && (
                        <div aria-hidden="true" className={`${styles.st_alert} ${styles['st_alert--success']}`}>
                            {t('label.createSuccess')}
                        </div>
                    )}
                    {actionStatus === 'createError' && (
                        <div aria-hidden="true" className={`${styles.st_alert} ${styles['st_alert--error']}`}>
                            {t('label.createError')}
                        </div>
                    )}
                    {actionStatus === 'clearSuccess' && (
                        <div aria-hidden="true" className={`${styles.st_alert} ${styles['st_alert--success']}`}>
                            {t('label.clearSuccess')}
                        </div>
                    )}
                    {actionStatus === 'clearError' && (
                        <div aria-hidden="true" className={`${styles.st_alert} ${styles['st_alert--error']}`}>
                            {t('label.clearError')}
                        </div>
                    )}

                    {/* Generated token display */}
                    {generatedToken && (
                        <div
                            ref={tokenBoxRef}
                            tabIndex={-1}
                            className={styles.st_tokenBox}
                            aria-labelledby="st-token-label"
                        >
                            <p id="st-token-label" className={styles.st_tokenWarning}>
                                {t('label.generatedToken')}
                            </p>
                            <span className={styles.st_tokenValue}>{generatedToken}</span>
                            <Button
                                type="button"
                                label={copied ? t('label.tokenCopied') : t('label.copyToken')}
                                variant="ghost"
                                onClick={handleCopy}
                            />
                        </div>
                    )}

                    {/* Existing tokens table */}
                    <div className={styles.st_section}>
                        <h3 id="st-tokens-heading" className={styles.st_sectionTitle}>{t('label.tokensTitle')}</h3>
                        {tokens === null || tokens === undefined ? (
                            <div className={styles.st_loading} role="status" aria-live="polite">
                                <Loader size="small" aria-hidden="true"/>
                            </div>
                        ) : tokens.length === 0 ? (
                            <span className={styles.st_emptyMsg}>{t('label.noTokens')}</span>
                        ) : (
                            <table className={styles.st_table} aria-labelledby="st-tokens-heading">
                                <thead>
                                    <tr>
                                        <th scope="col">{t('label.tokenCreatedDate')}</th>
                                        <th scope="col">{t('label.tokenRecipient')}</th>
                                        <th scope="col">{t('label.tokenDescription')}</th>
                                        <th scope="col">{t('label.tokenExpiration')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {tokens.map(token => (
                                        <tr key={token.createdDate}>
                                            <td><time dateTime={token.createdDate}>{formatDate(token.createdDate)}</time></td>
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
                        <h3 id="st-create-heading" className={styles.st_sectionTitle}>{t('label.createSection')}</h3>
                        <div className={styles.st_form}>
                            <div className={styles.st_formRow}>
                                <div className={styles.st_fieldGroup}>
                                    <label className={styles.st_label} htmlFor="st-recipient">{t('label.recipient')}</label>
                                    <input
                                        ref={recipientRef}
                                        id="st-recipient"
                                        type="email"
                                        autoComplete="email"
                                        required
                                        aria-required="true"
                                        aria-invalid={recipientError ? 'true' : undefined}
                                        aria-describedby={recipientError ? 'st-recipient-error' : undefined}
                                        className={`${styles.st_input}${recipientError ? ` ${styles['st_input--error']}` : ''}`}
                                        value={form.recipient}
                                        placeholder={t('label.recipientPlaceholder')}
                                        onChange={handleFormChange('recipient')}
                                    />
                                    {recipientError && (
                                        <span id="st-recipient-error" className={styles.st_errorMsg}>
                                            {recipientError}
                                        </span>
                                    )}
                                </div>
                                <div className={styles.st_fieldGroup}>
                                    <label className={styles.st_label} htmlFor="st-expiration">{t('label.expiration')}</label>
                                    <input
                                        id="st-expiration"
                                        type="number"
                                        min="1"
                                        autoComplete="off"
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
                                    autoComplete="off"
                                    className={styles.st_input}
                                    value={form.description}
                                    placeholder={t('label.descriptionPlaceholder')}
                                    onChange={handleFormChange('description')}
                                />
                            </div>
                        </div>
                        <div className={styles.st_actions}>
                            <Button
                                id="st-create-token"
                                type="button"
                                label={t('label.createToken')}
                                variant="primary"
                                isDisabled={busy}
                                onClick={handleCreate}
                            />
                            <span ref={clearAllBtnRef}>
                                <Button
                                    id="st-clear-all"
                                    type="button"
                                    label={t('label.clearAllTokens')}
                                    variant="destructive"
                                    isDisabled={busy || !tokens || tokens.length === 0}
                                    onClick={handleClearRequest}
                                />
                            </span>
                        </div>
                    </div>
                </>
            )}

            {/* Confirmation dialog for destructive Clear All action */}
            {showClearConfirm && (
                <div className={styles.st_dialogOverlay}>
                    <div
                        role="alertdialog"
                        aria-modal="true"
                        aria-labelledby="st-clear-dialog-title"
                        aria-describedby="st-clear-dialog-desc"
                        className={styles.st_dialog}
                        onKeyDown={handleDialogKeyDown}
                    >
                        <h3 id="st-clear-dialog-title" className={styles.st_dialogTitle}>
                            {t('label.clearConfirmTitle')}
                        </h3>
                        <p id="st-clear-dialog-desc" className={styles.st_dialogDesc}>
                            {t('label.clearConfirmMessage')}
                        </p>
                        <div className={styles.st_dialogActions}>
                            <button
                                ref={confirmBtnRef}
                                type="button"
                                className={styles.st_dialogConfirmBtn}
                                onClick={handleClearConfirm}
                            >
                                {t('label.clearConfirm')}
                            </button>
                            <button
                                ref={cancelBtnRef}
                                type="button"
                                className={styles.st_dialogCancelBtn}
                                onClick={handleClearCancel}
                            >
                                {t('label.cancel')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default SupportTokenAdmin;
