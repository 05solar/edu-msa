package main

const indexHTML = `<!doctype html><html lang=ko><head><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1"><title>공문서 오타·맞춤법 검사기</title>
<style>
:root{--line:#e2e8f0;--ink:#1e293b;--mut:#64748b;--blue:#2563eb;--bg:#f8fafc}
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
.t-맞춤법{background:#fee2e2;color:#991b1b}.t-띄어쓰기{background:#fef3c7;color:#92400e}.t-공백{background:#e0e7ff;color:#3730a3}.t-오탈자{background:#fee2e2;color:#991b1b}.t-동음이의{background:#cffafe;color:#155e75}
.o{color:#dc2626;text-decoration:line-through}.s{color:#166534;font-weight:600}
.ctx{color:#64748b;font-size:12px;margin-top:2px}
.empty{color:#94a3b8;text-align:center;padding:24px}
.ok{color:#166534;font-weight:600}
</style></head><body>
<header><h1>공문서 오타·맞춤법 검사기</h1><p>공문·안내문을 붙여넣고 검사하면 맞춤법·띄어쓰기·행정용어 오류를 찾아 교정본을 만들어 드립니다. (개인 단발 사용 도구)</p></header>
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
    if(rev.length)h+='<p style="color:#64748b;font-size:12px;margin-top:8px">※ 동음이의어는 자동 교정하지 않고 문맥 확인용으로만 표시합니다.</p>';
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
