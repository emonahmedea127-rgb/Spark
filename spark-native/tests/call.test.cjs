const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('app/src/main/assets/call.js', 'utf8');
function setup({ caller = true, denied = false, earlyIce = false } = {}) {
  const commands = [], nodes = {}, tracks = [{kind:'audio',enabled:true,stopped:false,stop(){this.stopped=true}}, {kind:'video',enabled:true,stopped:false,stop(){this.stopped=true}}];
  let polls = 0;
  const stream = {getTracks:()=>tracks,getAudioTracks:()=>[tracks[0]],getVideoTracks:()=>[tracks[1]]};
  class Peer {
    constructor(){this.connectionState='new';this.remote=null;this.added=[];this.closed=false;Peer.last=this;}
    addTrack(){}
    async createOffer(){return {type:'offer',sdp:'offer-sdp'}}
    async createAnswer(){assert.equal(this.remote.type,'offer');return {type:'answer',sdp:'answer-sdp'}}
    async setLocalDescription(d){this.local=d}
    async setRemoteDescription(d){this.remote=d;this.connectionState='connected';this.onconnectionstatechange?.()}
    async addIceCandidate(c){assert.ok(this.remote, 'ICE must wait for remote description');this.added.push(c)}
    close(){this.closed=true}
  }
  const context={console,document:{getElementById:id=>nodes[id]??=( {textContent:'',hidden:false,plays:0,play(){this.plays++;return Promise.resolve()}} )},navigator:{mediaDevices:{getUserMedia:async()=>{if(denied)throw Error('Permission denied');return stream}}},RTCPeerConnection:Peer,setTimeout:(f,ms)=>setTimeout(f,ms===1200?0:ms),clearTimeout,Date};
  context.window=context;
  context.SparkCall={send(command,json,id){const data=JSON.parse(json);commands.push({command,data});let result={ok:true};
    if(command==='poll'){polls++;result=earlyIce&&polls===1?
      {call:{status:'ringing'},ice:[{id:8,candidate:{candidate:'candidate:1'}}]}:
      (!earlyIce&&polls===1||earlyIce&&polls===2)?
      {call:{status:'accepted',offer:{type:'offer',sdp:'remote-offer'},answer:{type:'answer',sdp:'remote-answer'}},ice:earlyIce?[]:[{id:8,candidate:{candidate:'candidate:1'}}]}:
      {call:{status:'ended'},ice:[]};}
    queueMicrotask(()=>context.deliver(id,{result}));
  }};
  vm.createContext(context);vm.runInContext(source,context);
  return {context,commands,nodes,tracks,Peer,run:()=>context.boot({id:'call',user:'self',caller,video:true})};
}
test('Caller sends an offer, applies answer before ICE, advances cursor and releases devices on remote hangup',async()=>{
 const s=setup();await s.run();assert.equal(s.commands.filter(x=>x.command==='offer').length,1);assert.equal(s.commands.filter(x=>x.command==='answer').length,0);assert.equal(s.Peer.last.remote.type,'answer');assert.equal(s.Peer.last.added.length,1);assert.equal(s.commands.filter(x=>x.command==='poll')[1].data.after,8);assert.ok(s.tracks.every(x=>x.stopped));assert.ok(s.Peer.last.closed);
});
test('Recipient answers the received offer without generating a competing offer',async()=>{
 const s=setup({caller:false});await s.run();assert.equal(s.commands.filter(x=>x.command==='offer').length,0);assert.equal(s.commands.filter(x=>x.command==='answer').length,1);assert.equal(s.Peer.last.remote.type,'offer');assert.equal(s.Peer.last.added.length,1);
});
test('ICE candidates received before the answer are applied after remote description',async()=>{
 const s=setup({earlyIce:true});await s.run();
 assert.equal(s.Peer.last.added.length,1);
 assert.equal(s.Peer.last.remote.type,'answer');
 assert.equal(s.commands.filter(x=>x.command==='poll')[1].data.after,8);
});
test('Device permission failure ends signaling without opening a peer connection',async()=>{
 const s=setup({denied:true});await s.run();assert.equal(s.Peer.last,undefined);assert.ok(s.commands.some(x=>x.command==='end'));assert.match(s.nodes.hint.textContent,/Permission denied/);
});
test('Mute/camera controls change tracks and repeated boot does not duplicate a call',async()=>{
 const s=setup();await s.run();const before=s.commands.length;await s.run();assert.equal(s.commands.length,before);s.nodes.mute.onclick({target:s.nodes.mute});assert.equal(s.tracks[0].enabled,false);assert.equal(s.nodes.mute.textContent,'Unmute');s.nodes.camera.onclick({target:s.nodes.camera});assert.equal(s.tracks[1].enabled,false);
});

test('Incoming media is attached to an unmuted audio element and playback starts',async()=>{
 const s=setup();await s.run();const incoming={id:'remote-stream'};s.Peer.last.ontrack({streams:[incoming]});
 assert.equal(s.nodes.remoteAudio.srcObject,incoming);assert.equal(s.nodes.remoteAudio.muted,false);assert.equal(s.nodes.remoteAudio.volume,1);assert.equal(s.nodes.remoteAudio.plays,1);assert.equal(s.nodes.remote.srcObject,incoming);
});
test('Speaker button invokes Android audio routing and retries audio playback',async()=>{
 const s=setup();await s.run();await s.nodes.speaker.onclick({target:s.nodes.speaker});
 assert.ok(s.commands.some(c=>c.command==='speaker'));assert.equal(s.nodes.remoteAudio.plays,1);
});
