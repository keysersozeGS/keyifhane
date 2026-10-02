// Web Audio Synthesizer for Keyifhane Sound Effects
const KeyifhaneAudio = (function() {
    let ctx = null;

    function initCtx() {
        if (!ctx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (AudioContext) {
                ctx = new AudioContext();
            }
        }
        if (ctx && ctx.state === 'suspended') {
            ctx.resume();
        }
    }

    return {
        playCardFlip: function() {
            initCtx();
            if (!ctx) return;
            try {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(300, ctx.currentTime);
                osc.frequency.exponentialRampToValueAtTime(120, ctx.currentTime + 0.08);

                gain.gain.setValueAtTime(0.15, ctx.currentTime);
                gain.gain.linearRampToValueAtTime(0.01, ctx.currentTime + 0.08);

                osc.connect(gain);
                gain.connect(ctx.destination);

                osc.start();
                osc.stop(ctx.currentTime + 0.08);
            } catch (e) {}
        },

        playBidChime: function() {
            initCtx();
            if (!ctx) return;
            try {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
                osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.08); // E5

                gain.gain.setValueAtTime(0.12, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.25);

                osc.connect(gain);
                gain.connect(ctx.destination);

                osc.start();
                osc.stop(ctx.currentTime + 0.25);
            } catch (e) {}
        },

        playTrickWin: function() {
            initCtx();
            if (!ctx) return;
            try {
                const notes = [440, 554.37, 659.25]; // A4, C#5, E5
                notes.forEach((freq, idx) => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(freq, ctx.currentTime + idx * 0.06);

                    gain.gain.setValueAtTime(0.15, ctx.currentTime + idx * 0.06);
                    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + idx * 0.06 + 0.2);

                    osc.connect(gain);
                    gain.connect(ctx.destination);

                    osc.start(ctx.currentTime + idx * 0.06);
                    osc.stop(ctx.currentTime + idx * 0.06 + 0.2);
                });
            } catch (e) {}
        },

        playError: function() {
            initCtx();
            if (!ctx) return;
            try {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(150, ctx.currentTime);

                gain.gain.setValueAtTime(0.1, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15);

                osc.connect(gain);
                gain.connect(ctx.destination);

                osc.start();
                osc.stop(ctx.currentTime + 0.15);
            } catch (e) {}
        }
    };
})();
