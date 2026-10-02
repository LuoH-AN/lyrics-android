const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const html=fs.readFileSync('app/src/main/assets/lyrics_overlay.html','utf8');
for(const script of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g))new vm.Script(script[1]);
let stored='',requests=0;
const original={source:'QQ音乐',recordId:'a',title:'Song',lyrics:'original lyrics',wordLyrics:'words'};
const next={source:'QQ音乐',recordId:'b',title:'Song',lyrics:'another version'};
const context={console,TextEncoder,setTimeout:()=>1,clearTimeout:()=>{},requestAnimationFrame:()=>{},
  playback:{key:'song\u0000artist',track:'Song',artist:'Artist',durationMs:216000,requestId:7,candidates:[original],candidateIndex:0},
  automaticCandidates:[original],manualSourceKey:'',applyCandidate:()=>{},isUsableLyricPayload:x=>!!x,
  localStorage:{getItem:()=>null,setItem:()=>{}},window:{LobstaNativeLyrics:{
    readMatchMemory:()=>stored,writeMatchMemory:x=>{stored=x},rematchLyrics:()=>requests++
  }}};
vm.createContext(context);
vm.runInContext(html.slice(html.indexOf('    let sourceClickTimer='),html.indexOf('    function showMatchMenu()')),context);
vm.runInContext('matchNotice=()=>{};',context);
context.rematchCurrentSource();
assert.equal(JSON.parse(stored)[0].original.recordId,'a');
context.receiveRematch(7,1,next);
assert.equal(context.playback.candidates[0].recordId,'b');
assert.equal(JSON.parse(stored)[0].history.length,2);
context.rematchCurrentSource(true);
assert.equal(context.playback.candidates[0].recordId,'a');
assert.equal(requests,1,'restore must work offline without a new request');
assert.equal(JSON.parse(stored).length,0);
context.rematchCurrentSource();
context.refreshMatchManagement();
context.refreshMatchManagement();
assert.equal(context.playback.candidates[0].recordId,'a','unreviewed old selection must not override automatic');

// 主页自动写入(auto:true)的记忆也不得覆盖自动候选——只有手动固定才覆盖
stored=JSON.stringify([{key:'song\u0000artist|216|QQ音乐',at:Date.now(),title:'Song',artist:'Artist',source:'QQ音乐',candidate:next,auto:true}]);
context.playback.candidates=[original];context.playback.candidateIndex=0;
context.refreshMatchManagement();
assert.equal(context.playback.candidates[0].recordId,'a','auto (home-written) memory must not override automatic candidate');

// 自定义歌词候选：重新匹配必须提前返回，不发网络请求、不写记忆
const before=requests;
stored='';
context.playback.candidates=[{source:'自定义歌词',recordId:'c',lyrics:'custom lyrics',custom:true}];
context.playback.candidateIndex=0;
context.rematchCurrentSource();
assert.equal(requests,before,'custom lyrics must not trigger a rematch network request');
assert.equal(stored,'','custom lyrics rematch must not write match memory');

console.log('PASS: scripts parse, rematch history, offline rollback, stale-response guard, legacy-choice review, auto-memory guard, custom-lyrics no-rematch');
