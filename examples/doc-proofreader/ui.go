package main

const indexHTML = `<!doctype html><html lang=ko><head><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1"><title>공문서 오타·맞춤법 검사기</title>
<link rel="icon" type="image/svg+xml" href="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGRlZnM+PGxpbmVhckdyYWRpZW50IGlkPSJnIiB4MT0iMCIgeTE9IjAiIHgyPSIxIiB5Mj0iMSI+PHN0b3Agb2Zmc2V0PSIwIiBzdG9wLWNvbG9yPSIjMWQ0ZWQ4Ii8+PHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjM2I4MmY2Ii8+PC9saW5lYXJHcmFkaWVudD48L2RlZnM+PHJlY3Qgd2lkdGg9IjMyIiBoZWlnaHQ9IjMyIiByeD0iOCIgZmlsbD0idXJsKCNnKSIvPjxnIHRyYW5zZm9ybT0idHJhbnNsYXRlKDQgNCkiIGZpbGw9Im5vbmUiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIyIiBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiPjxwYXRoIGQ9Ik0xNCAzSDdhMiAyIDAgMCAwLTIgMnYxNGEyIDIgMCAwIDAgMiAyaDEwYTIgMiAwIDAgMCAyLTJWOHoiLz48cGF0aCBkPSJNMTQgM3Y1aDUiLz48cGF0aCBkPSJtOSAxNSAyIDIgNC00Ii8+PC9nPjwvc3ZnPg==">
<meta name="description" content="맞춤법·띄어쓰기·행정용어 오류를 찾아 교정본을 만드는 개인용 단발 도구">
<meta property="og:type" content="website">
<meta property="og:title" content="공문서 오타·맞춤법 검사기">
<meta property="og:description" content="맞춤법·띄어쓰기·행정용어 오류를 찾아 교정본을 만드는 개인용 단발 도구">
<meta property="og:image" content="http://doc-proofreader.localhost/og.png">
<meta property="og:url" content="http://doc-proofreader.localhost/">
<meta name="twitter:card" content="summary_large_image">
<style>
:root{--accent:#1d4ed8;--accent-2:#3b82f6;--line:#e6eaf1;--line-2:#eef1f6;--ink:#0f172a;--mut:#5b6b86;--faint:#9aa7bd;--blue:var(--accent);--bg:#eef1f8;--card:#fff;--ok:#0b7a4b;--danger:#dc2626}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,'Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg)}
header{background:#fff;border-bottom:1px solid var(--line);padding:16px 24px}header h1{font-size:20px;margin:0}header p{margin:4px 0 0;color:var(--mut);font-size:13px}
.wrap{max-width:1080px;margin:0 auto;padding:20px 24px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:820px){.grid{grid-template-columns:1fr}}
.panel{background:#fff;border:1px solid var(--line);border-radius:12px;padding:16px}
.panel h2{font-size:14px;margin:0 0 10px}
textarea{width:100%;min-height:280px;font:14px/1.6 system-ui,'Malgun Gothic',sans-serif;padding:12px;border:1px solid var(--line);border-radius:10px;resize:vertical;color:var(--ink);background:#fff}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-top:10px}
button{font:inherit;padding:8px 14px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink);cursor:pointer}
.btn-primary{background:var(--blue);color:#fff;border-color:var(--blue);font-weight:600}
.stats{display:flex;gap:18px;margin-bottom:12px;flex-wrap:wrap}.stat b{font-size:20px}.stat span{color:var(--mut);font-size:12px;display:block}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:top}th{color:var(--mut);font-size:11px}
.tag{display:inline-block;padding:1px 7px;border-radius:999px;font-size:11px;font-weight:700}
.t-맞춤법{background:#fee2e2;color:#991b1b}.t-띄어쓰기{background:#fef3c7;color:#92400e}.t-공백{background:#e0e7ff;color:#3730a3}.t-오탈자{background:#fee2e2;color:#991b1b}.t-동음이의{background:#cffafe;color:#155e75}.t-확인{background:#cffafe;color:#155e75}
.o{color:#dc2626;text-decoration:line-through}.s{color:#166534;font-weight:600}
.ctx{color:#64748b;font-size:12px;margin-top:2px}
.empty{color:#94a3b8;text-align:center;padding:24px}
.ok{color:#166534;font-weight:600}
/* === 디자인 고도화 (공통 디자인 시스템) === */
::selection{background:color-mix(in srgb,var(--accent) 22%,#fff)}
body{background:radial-gradient(1100px 480px at 100% -8%,color-mix(in srgb,var(--accent) 11%,transparent),transparent 62%),radial-gradient(720px 340px at -6% 0%,color-mix(in srgb,var(--accent) 7%,transparent),transparent 55%),var(--bg);min-height:100vh;-webkit-font-smoothing:antialiased;font-family:-apple-system,BlinkMacSystemFont,system-ui,'Malgun Gothic','Apple SD Gothic Neo',sans-serif}
header{position:sticky;top:0;z-index:30;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 24px;background:rgba(255,255,255,.8);-webkit-backdrop-filter:blur(12px);backdrop-filter:blur(12px);border-bottom:1px solid var(--line);box-shadow:0 1px 2px rgba(15,23,42,.05)}
.brand{display:flex;align-items:center;gap:13px;min-width:0}
.chip{flex:none;width:44px;height:44px;border-radius:13px;display:grid;place-items:center;background:linear-gradient(135deg,var(--accent),var(--accent-2));box-shadow:0 6px 16px color-mix(in srgb,var(--accent) 38%,transparent)}
.chip svg{width:23px;height:23px;stroke:#fff;fill:none;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}
.htxt{min-width:0}.htxt h1{margin:0;font-size:18px;font-weight:800;letter-spacing:-.02em;color:var(--ink);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.htxt p{margin:2px 0 0;color:var(--mut);font-size:12.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.hbadge{flex:none;font-size:11.5px;font-weight:700;color:var(--accent);background:color-mix(in srgb,var(--accent) 12%,#fff);border:1px solid color-mix(in srgb,var(--accent) 22%,#fff);padding:6px 12px;border-radius:999px;white-space:nowrap}
@media(max-width:620px){.hbadge{display:none}.htxt p{display:none}}
.panel{border-radius:16px;box-shadow:0 1px 2px rgba(15,23,42,.06),0 1px 3px rgba(15,23,42,.04);transition:box-shadow .2s}
.panel:hover{box-shadow:0 10px 30px rgba(15,23,42,.08)}
.panel h2{font-size:13px;font-weight:800;letter-spacing:-.01em;display:flex;align-items:center;gap:8px;color:var(--ink)}
.panel h2::before{content:"";flex:none;width:4px;height:14px;border-radius:3px;background:linear-gradient(var(--accent),var(--accent-2))}
label{font-weight:600;color:var(--mut)}
input,select,textarea{border-radius:10px;padding:9px 12px;border:1px solid var(--line);background:#fff;transition:border-color .15s,box-shadow .15s}
input:focus,select:focus,textarea:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 16%,transparent)}
button{border-radius:10px;font-weight:600;padding:9px 15px;transition:all .15s}
button:hover{background:#f6f8fc;border-color:#cfd6e4}
button:active{transform:translateY(1px)}
.btn-primary{background:linear-gradient(135deg,var(--accent),var(--accent-2));color:#fff;border:1px solid transparent;box-shadow:0 4px 13px color-mix(in srgb,var(--accent) 30%,transparent)}
.btn-primary:hover{filter:brightness(1.07);background:linear-gradient(135deg,var(--accent),var(--accent-2));border-color:transparent}
.btn-sm{padding:5px 10px;font-size:12px;border-radius:8px}
th{text-transform:uppercase;letter-spacing:.03em;font-weight:700;color:var(--mut)}
tr:last-child td{border-bottom:none}
.badge{font-weight:700;letter-spacing:-.01em}
dialog{border:none;border-radius:18px;box-shadow:0 24px 60px rgba(15,23,42,.28);padding:0}
dialog::backdrop{background:rgba(15,23,42,.42);-webkit-backdrop-filter:blur(2px);backdrop-filter:blur(2px)}
.empty,.empty-hint{color:var(--faint)}
.hintbox{background:color-mix(in srgb,var(--accent) 6%,#f6f8fc);border:1px solid color-mix(in srgb,var(--accent) 14%,#fff);border-radius:12px;color:#43506b}
.card{border-radius:12px;border-color:var(--line);transition:box-shadow .2s,transform .12s}.card:hover{box-shadow:0 6px 18px rgba(15,23,42,.07);transform:translateY(-1px)}
.stat b{font-weight:800}
.seat{border-radius:10px;box-shadow:0 1px 2px rgba(15,23,42,.05);transition:transform .1s,box-shadow .1s}.seat:hover{transform:translateY(-1px);box-shadow:0 4px 10px rgba(15,23,42,.1)}
.teacher{background:linear-gradient(135deg,color-mix(in srgb,var(--accent) 13%,#fff),color-mix(in srgb,var(--accent) 4%,#fff));border:1px solid color-mix(in srgb,var(--accent) 20%,#fff);color:var(--accent);font-weight:800;letter-spacing:.05em;border-radius:12px}
.drop{border-radius:14px;transition:all .15s;border-width:2px}.drop.drag{border-color:var(--accent);background:color-mix(in srgb,var(--accent) 8%,#fff);color:var(--accent)}
#svgbox,#chartImg,#preview,iframe{border-radius:12px}
.total b{background:linear-gradient(135deg,var(--accent),var(--accent-2));-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}
.conf{border-radius:12px}.tag{font-weight:700}
</style></head><body>
<header><div class="brand"><span class="chip"><svg viewBox="0 0 24 24"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="m9 15 2 2 4-4"/></svg></span><div class="htxt"><h1>공문서 오타·맞춤법 검사기</h1><p>맞춤법·띄어쓰기·행정용어 오류를 찾아 교정본을 만듭니다</p></div></div><span class="hbadge">교육청 업무도구 · 개인용</span></header>
<div class="wrap"><div class="grid">
  <div class="panel"><h2>원문 입력</h2>
    <textarea id="src" placeholder="검사할 공문서 내용을 붙여넣으세요."></textarea>
    <div class="toolbar">
      <button class="btn-primary" onclick="run()">오타 검사</button>
      <button onclick="document.getElementById('file').click()">파일(.txt) 열기</button>
      <input id="file" type="file" accept=".txt,text/plain" style="display:none" onchange="loadFile(event)">
      <button onclick="sample()">예시 넣기</button>
      <button onclick="clearAll()">지우기</button>
    </div>
  </div>
  <div class="panel"><h2>검사 결과</h2>
    <div class="stats" id="stats"></div>
    <div id="result"><div class="empty">왼쪽에 문서를 입력하고 [오타 검사]를 누르세요.</div></div>
  </div>
</div>
<div class="panel" id="corrPanel" style="margin-top:16px;display:none"><h2>교정본</h2>
  <textarea id="out" readonly></textarea>
  <div class="toolbar"><button class="btn-primary" onclick="copyOut()">교정본 복사</button><button onclick="downloadOut()">교정본 다운로드(.txt)</button></div>
</div>
</div>
<script>
function esc(s){s=s==null?'':(''+s);return s.replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c];});}
function ctxHtml(c){return esc(c).replace('《','<b style=color:#dc2626>').replace('》','</b>');}
function run(){
  var text=document.getElementById('src').value;
  if(!text.trim()){alert('검사할 텍스트를 입력하세요.');return;}
  fetch('/api/check',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:text})})
    .then(function(r){return r.json().then(function(d){return {ok:r.ok,d:d};});})
    .then(function(x){
      if(!x.ok){alert('오류: '+(x.d.error?x.d.error.message:''));return;}
      render(x.d);
    });
}
function render(d){
  var s=d.stats;
  document.getElementById('stats').innerHTML=
    stat(s.chars.toLocaleString('ko'),'검사 글자수')+stat(s.corrections,'교정 항목')+stat(s.issueGroups,'발견 유형');
  var corr=d.issues.filter(function(i){return i.kind==='correction';});
  var rev=d.issues.filter(function(i){return i.kind==='review';});
  var h='';
  if(!d.issues.length){h='<div class="ok">발견된 오류가 없습니다. 문서가 깨끗합니다.</div>';}
  else{
    h+='<table><thead><tr><th>표현</th><th>제안</th><th>유형</th><th>건수</th></tr></thead><tbody>';
    corr.forEach(function(i){h+=row(i);});
    rev.forEach(function(i){h+=row(i);});
    h+='</tbody></table>';
    if(rev.length)h+='<p style="color:#64748b;font-size:12px;margin-top:8px">※ 파랑 표시(동음이의·확인) 항목은 자동 교정하지 않고 문맥 확인용으로만 안내합니다.</p>';
  }
  document.getElementById('result').innerHTML=h;
  var cp=document.getElementById('corrPanel');
  if(s.changed){cp.style.display='block';document.getElementById('out').value=d.corrected;}
  else{cp.style.display='none';}
}
function row(i){
  var o=i.kind==='review'?('<span>'+esc(i.original)+'</span>'):('<span class=o>'+esc(i.original)+'</span>');
  var s=i.kind==='review'?('<span class=ctx>'+esc(i.suggestion)+'</span>'):('<span class=s>'+esc(i.suggestion)+'</span>');
  var ctx=i.context?('<div class=ctx>'+ctxHtml(i.context)+'</div>'):'';
  return '<tr><td>'+o+ctx+'</td><td>'+s+'</td><td><span class="tag t-'+i.rule+'">'+i.rule+'</span></td><td>'+i.count+'</td></tr>';
}
function stat(v,l){return '<div class="stat"><b>'+v+'</b><span>'+l+'</span></div>';}
function loadFile(e){var f=e.target.files[0];if(!f)return;var rd=new FileReader();rd.onload=function(){document.getElementById('src').value=rd.result;};rd.readAsText(f,'utf-8');}
function sample(){document.getElementById('src').value='붙임과 같이 회의 결과를 알려드리오니 참고바랍니다.\n금일  회의에서는 예산 집행 계획을 지양하기로 하였읍니다 .\n담당자는 몇일 내로 관련 자료를 제출바랍니다.\n계약 대금 결제 여부를 다시한번 확인바랍니다.';}
function clearAll(){document.getElementById('src').value='';document.getElementById('result').innerHTML='<div class="empty">왼쪽에 문서를 입력하고 [오타 검사]를 누르세요.</div>';document.getElementById('stats').innerHTML='';document.getElementById('corrPanel').style.display='none';}
function copyOut(){var t=document.getElementById('out');t.select();navigator.clipboard?navigator.clipboard.writeText(t.value).then(function(){toast('교정본을 복사했습니다.');}):document.execCommand('copy');}
function downloadOut(){var blob=new Blob([document.getElementById('out').value],{type:'text/plain;charset=utf-8'});var a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='교정본.txt';a.click();}
function toast(m){var t=document.createElement('div');t.textContent=m;t.style.cssText='position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#1e293b;color:#fff;padding:10px 16px;border-radius:8px;font-size:13px;z-index:9';document.body.appendChild(t);setTimeout(function(){t.remove();},1800);}
</script></body></html>`
