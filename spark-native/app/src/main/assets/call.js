'use strict';
const pending = new Map(); let serial = 0, stream, pc, stopped = false, config, after = 0, remoteSet = false, booted = false, everConnected = false, disconnectedAt = 0;
const statusEl = document.getElementById('status');
function request(command, data = {}) {
  return new Promise((resolve, reject) => {
    const id = ++serial;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('Connection timed out')); }, 20000);
    pending.set(id, { resolve, reject, timer });
    SparkCall.send(command, JSON.stringify(data), id);
  });
}
window.deliver = (id, response) => { const task = pending.get(id); if (!task) return; clearTimeout(task.timer); pending.delete(id); response.error ? task.reject(new Error(response.error)) : task.resolve(response.result); };
window.stopMedia = () => { stopped = true; stream?.getTracks().forEach(track => track.stop()); pc?.close(); };
async function hangup() { window.stopMedia(); statusEl.textContent = 'Call ended'; try { await request('end'); } catch (e) { document.getElementById('hint').textContent = e.message; } }
window.boot = async value => {
  if (booted) return; booted = true; config = value;
  document.getElementById('camera').hidden = !config.video;
  document.getElementById('local').hidden = !config.video;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: config.video ? { facingMode: 'user' } : false });
    document.getElementById('local').srcObject = stream;
    // Direct peer connectivity. A production TURN service is required for restrictive NATs.
    pc = new RTCPeerConnection({ iceServers: [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun.cloudflare.com:3478'] }] });
    stream.getTracks().forEach(track => pc.addTrack(track, stream));
    pc.ontrack = event => {
      const incoming = event.streams[0] || new MediaStream([event.track]);
      document.getElementById('remote').srcObject = incoming;
      const remoteAudio = document.getElementById('remoteAudio');
      remoteAudio.srcObject = incoming; remoteAudio.muted = false; remoteAudio.volume = 1;
      remoteAudio.play().catch(() => { document.getElementById('hint').textContent = 'Tap Speaker to enable audio playback'; });
    };
    pc.onicecandidate = event => { if (event.candidate) request('ice', event.candidate.toJSON()).catch(e => { document.getElementById('hint').textContent = e.message; }); };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') { everConnected = true; disconnectedAt = 0; statusEl.textContent = 'Connected'; document.getElementById('hint').textContent = config.video ? 'Video call' : 'Audio call'; }
      if (pc.connectionState === 'failed') { statusEl.textContent = 'Could not connect'; document.getElementById('hint').textContent = 'This network may require a TURN relay. Try a different Wi-Fi network.'; }
      if (pc.connectionState === 'disconnected') { disconnectedAt = Date.now(); statusEl.textContent = 'Reconnecting…'; }
    };
    if (config.caller) { const offer = await pc.createOffer(); await pc.setLocalDescription(offer); await request('offer', { type: offer.type, sdp: offer.sdp }); statusEl.textContent = 'Calling…'; }
    const started = Date.now();
    while (!stopped) {
      const result = await request('poll', { after });
      const call = result.call;
      if (!call || ['ended', 'declined'].includes(call.status)) { window.stopMedia(); statusEl.textContent = call?.status === 'declined' ? 'Call declined' : 'Call ended'; break; }
      if (!remoteSet) {
        if (!config.caller && call.offer) { await pc.setRemoteDescription(call.offer); const answer = await pc.createAnswer(); await pc.setLocalDescription(answer); await request('answer', { type: answer.type, sdp: answer.sdp }); remoteSet = true; statusEl.textContent = 'Connecting…'; }
        else if (config.caller && call.answer) { await pc.setRemoteDescription(call.answer); remoteSet = true; statusEl.textContent = 'Connecting…'; }
      }
      if (remoteSet) for (const item of result.ice) { await pc.addIceCandidate(item.candidate); after = Math.max(after, item.id); }
      if ((!everConnected && Date.now() - started > 90000) || (disconnectedAt && Date.now() - disconnectedAt > 20000)) { await hangup(); break; }
      await new Promise(resolve => setTimeout(resolve, 1200));
    }
  } catch (e) { window.stopMedia(); statusEl.textContent = 'Call unavailable'; document.getElementById('hint').textContent = e.message; try { await request('end'); } catch (_) {} }
};
document.getElementById('end').onclick = hangup;
document.getElementById('mute').onclick = event => { const track = stream?.getAudioTracks()[0]; if (track) { track.enabled = !track.enabled; event.target.textContent = track.enabled ? 'Mute' : 'Unmute'; } };
document.getElementById('camera').onclick = event => { const track = stream?.getVideoTracks()[0]; if (track) { track.enabled = !track.enabled; event.target.textContent = track.enabled ? 'Camera off' : 'Camera on'; } };

document.getElementById('speaker').onclick = async event => {
  try { const result = await request('speaker'); event.target.textContent = result.speaker ? 'Speaker on' : 'Speaker off'; await document.getElementById('remoteAudio').play(); }
  catch (e) { document.getElementById('hint').textContent = e.message; }
};
