document.addEventListener('DOMContentLoaded', async () => {
    const tabTitleEl = document.getElementById('tab-title');
    const tabUrlEl = document.getElementById('tab-url');
    const sendBtn = document.getElementById('send-btn');
    const msgEl = document.getElementById('msg');
    
    const settingsToggle = document.getElementById('settings-toggle');
    const settingsPanel = document.getElementById('settings-panel');
    const settingsArrow = document.getElementById('settings-arrow');
    
    const relayUrlInput = document.getElementById('relay-url');
    const secretTokenInput = document.getElementById('secret-token');
    const statusBadge = document.getElementById('status-badge');

    let currentTab = null;

    // Load active tab info
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs && tabs[0]) {
            currentTab = tabs[0];
            tabTitleEl.textContent = currentTab.title || 'No Title';
            tabUrlEl.textContent = currentTab.url || 'No URL';
            sendBtn.disabled = !currentTab.url || currentTab.url.startsWith('chrome://');
        } else {
            tabTitleEl.textContent = 'Unable to query tab';
            tabUrlEl.textContent = '';
            sendBtn.disabled = true;
        }
    });

    // Toggle settings panel
    settingsToggle.addEventListener('click', () => {
        const isOpen = settingsPanel.classList.toggle('open');
        settingsArrow.textContent = isOpen ? '▼' : '▶';
    });

    // Load settings from storage
    chrome.storage.local.get(['relayUrl', 'secretToken'], (result) => {
        const relayUrl = result.relayUrl || 'http://localhost:8082';
        const secretToken = result.secretToken || 'default-secret-token';
        
        relayUrlInput.value = relayUrl;
        secretTokenInput.value = secretToken;
        
        updateStatus(relayUrl, secretToken);
    });

    // Save settings on input change
    const saveSettings = () => {
        const relayUrl = relayUrlInput.value.trim().replace(/\/$/, ""); // trim trailing slash
        const secretToken = secretTokenInput.value.trim();
        
        chrome.storage.local.set({ relayUrl, secretToken }, () => {
            updateStatus(relayUrl, secretToken);
        });
    };

    relayUrlInput.addEventListener('input', saveSettings);
    secretTokenInput.addEventListener('input', saveSettings);

    // Update status badge (ping check to see if relay server is up)
    async function updateStatus(url, token) {
        if (!url) {
            statusBadge.textContent = 'No URL';
            statusBadge.className = 'status-badge';
            return;
        }
        try {
            const response = await fetch(`${url}/health`, { method: 'GET', mode: 'cors' }).catch(() => null);
            if (response && response.ok) {
                statusBadge.textContent = 'Server Online';
                statusBadge.className = 'status-badge connected';
            } else {
                statusBadge.textContent = 'Server Offline';
                statusBadge.className = 'status-badge';
            }
        } catch {
            statusBadge.textContent = 'Server Offline';
            statusBadge.className = 'status-badge';
        }
    }

    // Send action
    sendBtn.addEventListener('click', async () => {
        if (!currentTab || !currentTab.url) return;

        const relayUrl = relayUrlInput.value.trim().replace(/\/$/, "");
        const secretToken = secretTokenInput.value.trim();

        if (!relayUrl || !secretToken) {
            showMsg('Please configure settings first.', 'error');
            return;
        }

        sendBtn.disabled = true;
        sendBtn.textContent = 'Sending...';
        showMsg('Connecting to relay...', '');

        try {
            const response = await fetch(`${relayUrl}/send-url`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    url: currentTab.url,
                    token: secretToken
                })
            });

            const result = await response.json();

            if (response.ok && result.success) {
                showMsg(`Sent to ${result.deliveredTo} phone(s) successfully!`, 'success');
            } else {
                showMsg(result.error || 'Failed to deliver URL.', 'error');
            }
        } catch (err) {
            showMsg(`Connection failed: server unreachable.`, 'error');
        } finally {
            sendBtn.disabled = false;
            sendBtn.textContent = 'Send to Phone';
        }
    });

    function showMsg(text, type) {
        msgEl.style.display = text ? 'block' : 'none';
        msgEl.textContent = text;
        msgEl.className = 'message ' + type;
    }
});
