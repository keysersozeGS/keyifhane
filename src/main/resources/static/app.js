// Keyifhane Client Application Engine
(function() {
    let ws = null;
    let pendingWsQueue = [];
    let playerName = localStorage.getItem('keyifhane_player_name') || 'Oyuncu_' + Math.floor(100 + Math.random() * 900);
    let currentRoomState = null;
    let isConnected = false;
    let chatCollapsed = false;

    // Element references
    const elements = {};

    // ─── Toast Notification System (Chrome-safe, no alert()) ───────────────
    function showToast(message, type) {
        type = type || 'info';
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.style.cssText = 'position:fixed;top:70px;right:20px;z-index:9999;display:flex;flex-direction:column;gap:8px;pointer-events:none;';
            document.body.appendChild(container);
        }

        const toast = document.createElement('div');
        const colors = { success: '#2ed573', error: '#ff4757', info: '#f3c649', warn: '#ffa502' };
        toast.style.cssText = `
            background: rgba(15,23,42,0.95);
            border: 1.5px solid ${colors[type] || colors.info};
            color: white;
            padding: 12px 18px;
            border-radius: 10px;
            font-family: 'Inter', sans-serif;
            font-size: 13px;
            font-weight: 500;
            backdrop-filter: blur(12px);
            box-shadow: 0 4px 20px rgba(0,0,0,0.5);
            pointer-events: none;
            animation: toastIn 0.3s ease forwards;
            max-width: 280px;
        `;
        toast.textContent = message;
        container.appendChild(toast);

        // Remove after 2.5s
        setTimeout(function() {
            toast.style.opacity = '0';
            toast.style.transition = 'opacity 0.3s ease';
            setTimeout(function() { container.removeChild(toast); }, 300);
        }, 2500);
    }

    // Inject toast keyframe animation once
    const toastStyle = document.createElement('style');
    toastStyle.textContent = '@keyframes toastIn { from { opacity:0; transform:translateX(20px); } to { opacity:1; transform:translateX(0); } }';
    document.head.appendChild(toastStyle);
    // ───────────────────────────────────────────────────────────────────────

    function init() {
        cacheDOMElements();
        setupEventListeners();
        if (elements.playerNameInput) {
            elements.playerNameInput.value = playerName;
        }
        updateUserDisplayName(playerName);
        connectWebSocket();
        console.log('[Keyifhane] init() çalıştı. playerName:', playerName);
    }

    function cacheDOMElements() {
        elements.playerNameInput = document.getElementById('player-name-input');
        elements.btnSaveName = document.getElementById('btn-save-name');
        elements.userDisplayName = document.getElementById('user-display-name');
        elements.userStatusDot = document.getElementById('user-status-dot');
        elements.roomListGrid = document.getElementById('room-list-grid');
        elements.myHandContainer = document.getElementById('my-hand-container');
        elements.tableCenterMsg = document.getElementById('table-center-msg');
        elements.biddingPanel = document.getElementById('bidding-panel');
        elements.trumpPanel = document.getElementById('trump-panel');
        elements.startGameOverlay = document.getElementById('start-game-overlay');
        elements.nextRoundOverlay = document.getElementById('next-round-overlay');
        elements.chatMessagesList = document.getElementById('chat-messages-list');
        elements.chatInput = document.getElementById('chat-input');
        elements.roomTitleDisplay = document.getElementById('room-title-display');
        elements.roomCodeDisplay = document.getElementById('room-code-display');
        elements.gameRoundInfo = document.getElementById('game-round-info');
        elements.gameTrumpDisplay = document.getElementById('game-trump-display');
        elements.scoreboardBody = document.getElementById('scoreboard-body');
        elements.cardBatakBtn = document.getElementById('btn-open-batak-lobby');
    }

    function setupEventListeners() {
        if (elements.btnSaveName) {
            elements.btnSaveName.addEventListener('click', function() {
                console.log('[Keyifhane] Kaydet butonuna tıklandı');
                savePlayerName();
            });
        }
        if (elements.playerNameInput) {
            elements.playerNameInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') savePlayerName();
            });
        }
        if (elements.cardBatakBtn) {
            elements.cardBatakBtn.addEventListener('click', function() {
                console.log('[Keyifhane] Masalara Katıl tıklandı');
                openLobby('BATAK');
            });
        }
        if (elements.chatInput) {
            elements.chatInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') sendChat();
            });
        }
        console.log('[Keyifhane] Event listener\'lar bağlandı. btnSaveName:', !!elements.btnSaveName, 'cardBatakBtn:', !!elements.cardBatakBtn);
    }

    function savePlayerName() {
        console.log('[Keyifhane] savePlayerName() çağrıldı');
        const input = elements.playerNameInput || document.getElementById('player-name-input');
        if (!input) {
            console.warn('[Keyifhane] player-name-input elementi bulunamadı!');
            showToast('Hata: İsim alanı bulunamadı.', 'error');
            return;
        }
        const val = input.value.trim();
        console.log('[Keyifhane] Girilen isim:', val);
        if (val) {
            playerName = val;
            localStorage.setItem('keyifhane_player_name', playerName);
            updateUserDisplayName(playerName);
            showToast('✓ Oyuncu adı kaydedildi: ' + playerName, 'success');
        } else {
            showToast('Lütfen geçerli bir isim girin.', 'warn');
        }
    }

    function updateUserDisplayName(name) {
        const disp = elements.userDisplayName || document.getElementById('user-display-name');
        if (disp) {
            disp.textContent = name;
        }
    }

    function connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/game`;

        try {
            ws = new WebSocket(wsUrl);
        } catch (e) {
            console.error('WebSocket bağlantı hatası:', e);
            return;
        }

        ws.onopen = function() {
            isConnected = true;
            const dot = elements.userStatusDot || document.getElementById('user-status-dot');
            if (dot) {
                dot.className = 'status-dot connected';
            }
            console.log('Keyifhane WebSocket sunucusuna bağlandı.');
            
            // Flush pending queued actions
            while (pendingWsQueue.length > 0) {
                const actionPayload = pendingWsQueue.shift();
                ws.send(JSON.stringify(actionPayload));
            }

            requestLobby();
        };

        ws.onmessage = function(event) {
            try {
                const data = JSON.parse(event.data);
                handleServerMessage(data);
            } catch (e) {
                console.error('Mesaj işleme hatası:', e);
            }
        };

        ws.onclose = function() {
            isConnected = false;
            const dot = elements.userStatusDot || document.getElementById('user-status-dot');
            if (dot) {
                dot.className = 'status-dot disconnected';
            }
            console.warn('WebSocket bağlantısı koptu. 3 saniye sonra yeniden bağlanılıyor...');
            setTimeout(connectWebSocket, 3000);
        };

        ws.onerror = function(err) {
            console.error('WebSocket hatası:', err);
        };
    }

    function sendWs(actionPayload) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(actionPayload));
        } else if (ws && ws.readyState === WebSocket.CONNECTING) {
            console.log('Bağlantı kuruluyor, istek sıraya alındı...', actionPayload);
            pendingWsQueue.push(actionPayload);
        } else {
            console.warn('Sunucu bağlantısı kapalı, yeniden bağlanmayı deniyor...');
            pendingWsQueue.push(actionPayload);
            connectWebSocket();
        }
    }

    function requestLobby() {
        sendWs({ action: 'GET_LOBBY' });
    }

    function handleServerMessage(data) {
        if (data.type === 'LOBBY_STATE') {
            renderLobbyState(data.rooms);
        } else if (data.type === 'ROOM_STATE') {
            currentRoomState = data;
            showView('view-game');
            renderRoomState(data);
        } else if (data.type === 'ERROR') {
            if (typeof KeyifhaneAudio !== 'undefined') {
                KeyifhaneAudio.playError();
            }
            showToast('Hata: ' + data.message, 'error');
        }
    }

    function showView(viewId) {
        document.querySelectorAll('.view-screen').forEach(el => {
            el.classList.remove('active');
        });
        const target = document.getElementById(viewId);
        if (target) {
            target.classList.add('active');
        }
    }

    function openLobby(gameType) {
        showView('view-lobby');
        requestLobby();
    }

    function renderLobbyState(rooms) {
        const grid = elements.roomListGrid || document.getElementById('room-list-grid');
        if (!grid) return;
        grid.innerHTML = '';
        if (!rooms || rooms.length === 0) {
            grid.innerHTML = '<div class="empty-state">Henüz açık masa yok. Hemen yeni bir masa oluşturun!</div>';
            return;
        }

        rooms.forEach(r => {
            const card = document.createElement('div');
            card.className = 'room-card';
            card.innerHTML = `
                <div class="room-card-title">${r.roomName}</div>
                <div class="room-card-details">
                    <span>Masa Kodu: <strong>${r.roomId}</strong></span>
                    <span>Oyuncu: ${r.playerCount}/4</span>
                </div>
                <button class="btn btn-sm btn-primary" onclick="keyifhaneApp.joinRoom('${r.roomId}')">Masaya Otur</button>
            `;
            grid.appendChild(card);
        });
    }

    function quickPlay() {
        sendWs({ action: 'QUICK_PLAY', playerName: playerName });
    }

    function showCreateRoomModal() {
        const modal = document.getElementById('modal-create-room');
        if (modal) modal.classList.remove('hidden');
    }

    function submitCreateRoom() {
        const roomNameInput = document.getElementById('create-room-name');
        const roomName = roomNameInput ? roomNameInput.value.trim() : '';
        closeModal('modal-create-room');
        sendWs({ action: 'CREATE_ROOM', roomName: roomName, playerName: playerName });
    }

    function joinRoom(roomId) {
        sendWs({ action: 'JOIN_ROOM', roomId: roomId, playerName: playerName });
    }

    function joinRoomByCode() {
        const codeInput = document.getElementById('join-room-code-input');
        const code = codeInput ? codeInput.value.trim().toUpperCase() : '';
        if (code) {
            joinRoom(code);
        } else {
            showToast('Lütfen geçerli bir masa kodu girin.', 'warn');
        }
    }

    function renderRoomState(state) {
        const roomTitleDisplay = elements.roomTitleDisplay || document.getElementById('room-title-display');
        const roomCodeDisplay = elements.roomCodeDisplay || document.getElementById('room-code-display');
        const gameRoundInfo = elements.gameRoundInfo || document.getElementById('game-round-info');
        const gameTrumpDisplay = elements.gameTrumpDisplay || document.getElementById('game-trump-display');

        if (roomTitleDisplay) roomTitleDisplay.textContent = state.roomName;
        if (roomCodeDisplay) roomCodeDisplay.textContent = state.roomId;
        if (gameRoundInfo) gameRoundInfo.textContent = `Tur ${state.currentRound} / ${state.totalRounds}`;

        const trumpMap = { 'SPADES': '♠ Maça', 'HEARTS': '♥ Kupa', 'DIAMONDS': '♦ Karo', 'CLUBS': '♣ Sinek' };
        if (gameTrumpDisplay) gameTrumpDisplay.textContent = 'Koz: ' + (state.trumpSuit ? trumpMap[state.trumpSuit] : '-');

        const mySeat = state.mySeatIndex;
        const isWaitingCollection = !!state.trickWaitingCollection;
        const trickWinnerSeat = state.trickWinnerSeat;
        const winningCardId = state.winningCardId;

        // Seat Mapping Relative to mySeat (0=South, 1=West, 2=North, 3=East)
        const seatOffsets = [0, 1, 2, 3];
        seatOffsets.forEach(offset => {
            const actualSeat = (mySeat + offset) % 4;
            const p = state.players[actualSeat];
            const seatElement = document.getElementById(`seat-${offset}`);

            if (seatElement && p) {
                const nameEl = seatElement.querySelector('.p-name');
                const bidEl = seatElement.querySelector('.p-bid');
                const tricksEl = seatElement.querySelector('.p-tricks');

                if (nameEl) nameEl.textContent = p.name + (p.isBot ? ' 🤖' : '');
                if (bidEl) bidEl.textContent = 'İhale: ' + (p.bid > 0 ? p.bid : (p.bid === 0 ? 'PAS' : '-'));
                if (tricksEl) tricksEl.textContent = 'El: ' + p.tricksWon;

                if (state.currentTurnSeat === actualSeat && 
                    state.gamePhase !== 'WAITING_FOR_PLAYERS' && 
                    state.gamePhase !== 'ROUND_OVER' && 
                    state.gamePhase !== 'GAME_OVER' && 
                    !isWaitingCollection) {
                    seatElement.classList.add('active-turn');
                } else {
                    seatElement.classList.remove('active-turn');
                }

                // Card count badge for opponents
                if (offset !== 0) {
                    const badge = document.getElementById(`card-count-${offset}`);
                    if (badge) badge.textContent = p.handCount;
                }
            }
        });

        // Reset winner effects on all seats first
        for (let i = 0; i < 4; i++) {
            const seatEl = document.getElementById(`seat-${i}`);
            if (seatEl) {
                seatEl.classList.remove('trick-winner-effect');
                const badge = seatEl.querySelector('.trick-win-pop');
                if (badge) badge.remove();
            }
        }

        // Center Trick Mat Cards
        const slotDirs = ['south', 'west', 'north', 'east'];
        for (let i = 0; i < 4; i++) {
            const slot = document.getElementById(`slot-${i}`);
            if (slot) {
                slot.className = `played-card-slot slot-${slotDirs[i]}`;
                slot.innerHTML = '';
            }
        }

        const tableCenterMsg = elements.tableCenterMsg || document.getElementById('table-center-msg');
        if (state.currentTrick && state.currentTrick.length > 0) {
            state.currentTrick.forEach(played => {
                const seatIndex = played.seatIndex;
                const offset = (seatIndex - mySeat + 4) % 4;
                const slot = document.getElementById(`slot-${offset}`);

                if (slot) {
                    const isWinningCard = (isWaitingCollection && played.cardId === winningCardId);
                    const cardEl = createCardElement(played, false, isWinningCard);
                    slot.appendChild(cardEl);
                }
            });

            if (isWaitingCollection && trickWinnerSeat !== undefined && trickWinnerSeat !== null && trickWinnerSeat >= 0) {
                const winnerPlayer = state.players[trickWinnerSeat];
                const winnerOffset = (trickWinnerSeat - mySeat + 4) % 4;
                const winnerSeatEl = document.getElementById(`seat-${winnerOffset}`);

                if (winnerSeatEl) {
                    winnerSeatEl.classList.add('trick-winner-effect');
                    const popBadge = document.createElement('div');
                    popBadge.className = 'trick-win-pop';
                    popBadge.textContent = '🏆 +1 El!';
                    winnerSeatEl.appendChild(popBadge);
                }

                if (tableCenterMsg) {
                    tableCenterMsg.style.display = 'block';
                    tableCenterMsg.className = 'center-status-msg winner-highlight-msg';
                    const winName = (trickWinnerSeat === mySeat) ? 'Tebrikler, eli siz kazandınız!' : `${winnerPlayer ? winnerPlayer.name : 'Oyuncu'} eli kazandı!`;
                    tableCenterMsg.textContent = winName;
                }

                // Play sound once per completed trick
                const trickKey = `${state.currentRound}_${state.currentTrick.length}_${trickWinnerSeat}_${winningCardId}`;
                if (window.lastCelebratedTrick !== trickKey) {
                    window.lastCelebratedTrick = trickKey;
                    if (typeof KeyifhaneAudio !== 'undefined') {
                        KeyifhaneAudio.playTrickWin();
                    }
                }

                // Trigger sweep animation towards winner seat before server collects
                clearTimeout(window.trickCollectAnimTimer);
                window.trickCollectAnimTimer = setTimeout(() => {
                    const dirClass = `fly-to-seat-${winnerOffset}`;
                    for (let i = 0; i < 4; i++) {
                        const slot = document.getElementById(`slot-${i}`);
                        if (slot) slot.classList.add(dirClass);
                    }
                }, 1000);
            } else {
                if (tableCenterMsg) tableCenterMsg.style.display = 'none';
            }
        } else {
            if (tableCenterMsg) {
                tableCenterMsg.className = 'center-status-msg';
                tableCenterMsg.style.display = 'block';
                if (state.gamePhase === 'BIDDING') {
                    tableCenterMsg.textContent = 'İhale Turu Devam Ediyor...';
                } else if (state.gamePhase === 'SELECTING_TRUMP') {
                    tableCenterMsg.textContent = 'Koz Seçiliyor...';
                } else if (state.gamePhase === 'PLAYING_TRICKS') {
                    tableCenterMsg.textContent = 'Kartınızı Masaya Oynayın';
                } else if (state.gamePhase === 'WAITING_FOR_PLAYERS') {
                    tableCenterMsg.textContent = 'Oyuncular Bekleniyor...';
                }
            }
        }

        // Render Hand (MY CARDS)
        const myHandContainer = elements.myHandContainer || document.getElementById('my-hand-container');
        if (myHandContainer) {
            myHandContainer.innerHTML = '';
            const isMyTurn = (state.gamePhase === 'PLAYING_TRICKS' && state.currentTurnSeat === mySeat && !isWaitingCollection);

            if (state.myHand) {
                // Siyah - Kırmızı - Siyah - Kırmızı sıralaması: Maça (Siyah) -> Kupa (Kırmızı) -> Sinek (Siyah) -> Karo (Kırmızı)
                const suitOrder = { 'SPADES': 0, 'HEARTS': 1, 'CLUBS': 2, 'DIAMONDS': 3 };
                const sortedHand = [...state.myHand].sort((a, b) => {
                    const sDiff = (suitOrder[a.suit] !== undefined ? suitOrder[a.suit] : 99) - 
                                  (suitOrder[b.suit] !== undefined ? suitOrder[b.suit] : 99);
                    if (sDiff !== 0) return sDiff;
                    return a.value - b.value;
                });

                sortedHand.forEach(cardData => {
                    const isLegal = isMyTurn && state.legalCardIds && state.legalCardIds.includes(cardData.id);
                    const cardEl = createCardElement(cardData, isLegal, false);

                    if (isLegal) {
                        cardEl.classList.add('selectable');
                        cardEl.onclick = function() {
                            playCard(cardData.id);
                        };
                    } else if (isMyTurn) {
                        cardEl.classList.add('disabled-card');
                    }
                    myHandContainer.appendChild(cardEl);
                });
            }
        }

        // Overlays visibility
        const biddingPanel = elements.biddingPanel || document.getElementById('bidding-panel');
        const trumpPanel = elements.trumpPanel || document.getElementById('trump-panel');
        const startGameOverlay = elements.startGameOverlay || document.getElementById('start-game-overlay');
        const nextRoundOverlay = elements.nextRoundOverlay || document.getElementById('next-round-overlay');

        if (biddingPanel) biddingPanel.classList.toggle('hidden', !(state.gamePhase === 'BIDDING' && state.currentTurnSeat === mySeat));
        if (trumpPanel) trumpPanel.classList.toggle('hidden', !(state.gamePhase === 'SELECTING_TRUMP' && state.highestBidderSeat === mySeat));
        if (startGameOverlay) startGameOverlay.classList.toggle('hidden', !(state.gamePhase === 'WAITING_FOR_PLAYERS'));
        if (nextRoundOverlay) nextRoundOverlay.classList.toggle('hidden', !(state.gamePhase === 'ROUND_OVER'));

        // Render Chat
        renderChat(state.chatMessages);

        // Update Scoreboard Modal
        renderScoreboard(state.players);
    }

    function createCardElement(card, isSelectable, isWinningCard) {
        const div = document.createElement('div');
        div.className = `playing-card ${card.color.toLowerCase()}`;
        if (isWinningCard) {
            div.classList.add('winner-card');
        }

        div.innerHTML = `
            <div class="card-corner">
                <span class="card-rank">${card.rankLabel}</span>
                <span class="card-suit">${card.symbol}</span>
            </div>
            <div class="card-center-symbol">${card.symbol}</div>
            ${isWinningCard ? '<div class="winner-crown" title="Eli Kazanan Kart">👑</div>' : ''}
        `;
        return div;
    }

    function sendBid(bid) {
        if (typeof KeyifhaneAudio !== 'undefined') KeyifhaneAudio.playBidChime();
        sendWs({ action: 'PLACE_BID', bid: bid });
    }

    function sendTrump(suit) {
        if (typeof KeyifhaneAudio !== 'undefined') KeyifhaneAudio.playBidChime();
        sendWs({ action: 'SELECT_TRUMP', suit: suit });
    }

    function playCard(cardId) {
        if (typeof KeyifhaneAudio !== 'undefined') KeyifhaneAudio.playCardFlip();
        sendWs({ action: 'PLAY_CARD', cardId: cardId });
    }

    function startGame() {
        sendWs({ action: 'START_GAME' });
    }

    function nextRound() {
        sendWs({ action: 'NEXT_ROUND' });
    }

    function sendChat() {
        const input = elements.chatInput || document.getElementById('chat-input');
        if (!input) return;
        const text = input.value.trim();
        if (text) {
            sendWs({ action: 'SEND_CHAT', text: text });
            input.value = '';
        }
    }

    function renderChat(messages) {
        const list = elements.chatMessagesList || document.getElementById('chat-messages-list');
        if (!messages || !list) return;
        list.innerHTML = '';
        messages.forEach(msg => {
            const mDiv = document.createElement('div');
            mDiv.className = 'chat-msg';
            mDiv.innerHTML = `<span class="sender">${msg.sender}:</span> ${msg.text}`;
            list.appendChild(mDiv);
        });
        list.scrollTop = list.scrollHeight;
    }

    function renderScoreboard(players) {
        const body = elements.scoreboardBody || document.getElementById('scoreboard-body');
        if (!players || !body) return;
        body.innerHTML = '';
        players.forEach(p => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${p.name} ${p.isBot ? '🤖' : ''}</td>
                <td>${p.bid > 0 ? p.bid : 'PAS'}</td>
                <td>${p.tricksWon}</td>
                <td><strong>${p.score}</strong></td>
            `;
            body.appendChild(tr);
        });
    }

    function toggleScoreboard() {
        const modal = document.getElementById('modal-scoreboard');
        if (modal) modal.classList.toggle('hidden');
    }

    function toggleChat() {
        const chatBox = document.getElementById('chat-box');
        const icon = document.getElementById('chat-toggle-icon');
        if (chatBox && icon) {
            if (chatCollapsed) {
                chatBox.style.height = 'auto';
                icon.textContent = '▲';
            } else {
                chatBox.style.height = '35px';
                icon.textContent = '▼';
            }
            chatCollapsed = !chatCollapsed;
        }
    }

    function closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) modal.classList.add('hidden');
    }

    function leaveRoom() {
        sendWs({ action: 'LEAVE_ROOM' });
        showView('view-landing');
    }

    function copyRoomCode() {
        if (currentRoomState && currentRoomState.roomId) {
            navigator.clipboard.writeText(currentRoomState.roomId);
            showToast('✓ Masa Kodu kopyalandı: ' + currentRoomState.roomId, 'success');
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Explicitly bind to window.keyifhaneApp for inline HTML event handlers
    window.keyifhaneApp = {
        savePlayerName: savePlayerName,
        showView: showView,
        openLobby: openLobby,
        quickPlay: quickPlay,
        showCreateRoomModal: showCreateRoomModal,
        submitCreateRoom: submitCreateRoom,
        joinRoom: joinRoom,
        joinRoomByCode: joinRoomByCode,
        sendBid: sendBid,
        sendTrump: sendTrump,
        playCard: playCard,
        startGame: startGame,
        nextRound: nextRound,
        sendChat: sendChat,
        leaveRoom: leaveRoom,
        toggleScoreboard: toggleScoreboard,
        toggleChat: toggleChat,
        closeModal: closeModal,
        copyRoomCode: copyRoomCode
    };
})();


