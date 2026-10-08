'use strict';
const pending = new Map(); let serial = 0, stream, pc, stopped = false, config, after = 0, remoteSet = false, booted = false, everConnected = false, disconnectedAt = 0;
const queuedIce = [];
let fallbackRemoteStream;
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
window.stopMedia = () => { stopped = true; if (stream) stream.getTracks().forEach(track => track.stop()); if (pc) pc.close(); };
async function hangup() { window.stopMedia(); statusEl.textContent = 'Call ended'; try { await request('end'); } catch (e) { document.getElementById('hint').textContent = e.message; } }
window.boot = async value => {
  if (booted) return; booted = true; config = value;
  document.getElementById('camera').hidden = !config.video;
  document.getElementById('local').hidden = !config.video;
  let stage = 'Microphone/camera';
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: config.video ? { facingMode: 'user' } : false });
    if (stopped) { stream.getTracks().forEach(track => track.stop()); return; }
    document.getElementById('local').srcObject = stream;
    let servers = [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun.cloudflare.com:3478'] }];
    try { const relay = await request('relay'); if (Array.isArray(relay.iceServers) && relay.iceServers.length) servers = relay.iceServers; }
    catch (_) { document.getElementById('hint').textContent = 'Relay unavailable; trying a direct connection'; }
    if (stopped) return;
    stage = 'Connection setup';
    pc = new RTCPeerConnection({ iceServers: servers });
    stream.getTracks().forEach(track => pc.addTrack(track, stream));
    pc.ontrack = event => {
      let incoming = event.streams && event.streams[0];
      if (!incoming) {
        if (!fallbackRemoteStream) fallbackRemoteStream = new MediaStream();
        if (!fallbackRemoteStream.getTracks().some(track => track.id === event.track.id)) fallbackRemoteStream.addTrack(event.track);
        incoming = fallbackRemoteStream;
      }
      // The video element is muted; only the audio element plays remote sound.
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
    stage = 'Sending call offer';
    if (config.caller) { const offer = await pc.createOffer(); await pc.setLocalDescription(offer); await request('offer', { type: offer.type, sdp: offer.sdp }); statusEl.textContent = 'Calling…'; }
    stage = 'Waiting for answer';
    const started = Date.now();
    let pollFailures = 0;
    while (!stopped) {
      let result;
      try { result = await request('poll', { after }); pollFailures = 0; }
      catch (error) {
        if (stopped) break;
        // One transient signaling failure must not tear down a working media connection.
        if (++pollFailures >= 3) throw error;
        document.getElementById('hint').textContent = 'Signaling interrupted; retrying…';
        await new Promise(resolve => setTimeout(resolve, 1200));
        continue;
      }
      if (stopped) break;
      const call = result.call;
      if (!call || ['ended', 'declined'].includes(call.status)) { window.stopMedia(); statusEl.textContent = call && call.status === 'declined' ? 'Call declined' : 'Call ended'; break; }
      if (!remoteSet) {
        if (!config.caller && call.offer) { stage = 'Answering call'; await pc.setRemoteDescription(call.offer); const answer = await pc.createAnswer(); await pc.setLocalDescription(answer); await request('answer', { type: answer.type, sdp: answer.sdp }); remoteSet = true; statusEl.textContent = 'Connecting…'; }
        else if (config.caller && call.answer) { await pc.setRemoteDescription(call.answer); remoteSet = true; statusEl.textContent = 'Connecting…'; }
      }
      for (const item of result.ice) { queuedIce.push(item.candidate); after = Math.max(after, item.id); }
      if (remoteSet) while (queuedIce.length) await pc.addIceCandidate(queuedIce.shift());
      if ((!everConnected && Date.now() - started > 90000) || (disconnectedAt && Date.now() - disconnectedAt > 20000)) { await hangup(); break; }
      await new Promise(resolve => setTimeout(resolve, 1200));
    }
  } catch (e) { window.stopMedia(); statusEl.textContent = 'Call unavailable'; document.getElementById('hint').textContent = stage + ': ' + e.message; document.getElementById('end').textContent = 'Close'; try { await request('failed'); } catch (_) {} }
};
document.getElementById('end').onclick = hangup;
document.getElementById('mute').onclick = event => { const track = stream && stream.getAudioTracks()[0]; if (track) { track.enabled = !track.enabled; event.target.textContent = track.enabled ? 'Mute' : 'Unmute'; } };
document.getElementById('camera').onclick = event => { const track = stream && stream.getVideoTracks()[0]; if (track) { track.enabled = !track.enabled; event.target.textContent = track.enabled ? 'Camera off' : 'Camera on'; } };

document.getElementById('speaker').onclick = async event => {
  try { const result = await request('speaker'); event.target.textContent = result.speaker ? 'Speaker on' : 'Speaker off'; await document.getElementById('remoteAudio').play(); }
  catch (e) { document.getElementById('hint').textContent = e.message; }
};
