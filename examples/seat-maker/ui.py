INDEX = r"""<!doctype html><html lang=ko><head><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1"><title>학생 자리배치 생성기</title>
<style>
:root{--line:#e2e8f0;--ink:#1e293b;--mut:#64748b;--blue:#2563eb;--bg:#f8fafc}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,'Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg)}
header{background:#fff;border-bottom:1px solid var(--line);padding:16px 24px}header h1{font-size:20px;margin:0}header p{margin:4px 0 0;color:var(--mut);font-size:13px}
.wrap{max-width:1120px;margin:0 auto;padding:20px 24px}
.grid2{display:grid;grid-template-columns:340px 1fr;gap:18px}@media(max-width:860px){.grid2{grid-template-columns:1fr}}
.panel{background:#fff;border:1px solid var(--line);border-radius:12px;padding:16px}
.panel h2{font-size:14px;margin:0 0 10px}
label{font-size:12px;color:var(--mut);display:block;margin:10px 0 4px}
textarea,input,select{width:100%;font:14px system-ui,'Malgun Gothic',sans-serif;padding:8px 10px;border:1px solid var(--line);border-radius:8px;color:var(--ink);background:#fff}
textarea{min-height:150px;resize:vertical}
.row{display:flex;gap:8px}.row>div{flex:1}
button{font:inherit;padding:8px 14px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink);cursor:pointer}
.btn-primary{background:var(--blue);color:#fff;border-color:var(--blue);font-weight:600}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}
.board{margin:0 auto;max-width:100%;overflow-x:auto}
.teacher{background:#e8edf5;border:1px solid var(--line);border-radius:8px;text-align:center;padding:8px;font-weight:700;color:#334155;margin-bottom:14px}
table.seats{border-collapse:separate;border-spacing:8px;margin:0 auto}
.seat{width:88px;height:52px;border:1px solid var(--line);border-radius:8px;display:flex;align-items:center;justify-content:center;font-weight:600;font-size:14px;background:#fff}
.seat.m{background:#eff6ff;border-color:#bfdbfe}.seat.f{background:#fdf2f8;border-color:#fbcfe8}.seat.empty{background:#f1f5f9;color:#cbd5e1;font-weight:400}
.warn{background:#fef3c7;color:#92400e;border-radius:8px;padding:8px 12px;font-size:13px;margin-bottom:10px}
.ok2{color:#166534;font-size:13px;margin-bottom:8px}
.empty-hint{color:#94a3b8;text-align:center;padding:40px}
@media print{header,.panel.input,.toolbar,.no-print{display:none!important}.grid2{grid-template-columns:1fr}.wrap{padding:0}.panel{border:none}}
</style></head><body>
<header><h1>학생 자리배치 생성기</h1><p>명단을 입력하거나 엑셀로 올리고 조건을 지정하면 좌석 배치를 만들어 엑셀·인쇄로 내보냅니다. (개인 단발 사용 도구)</p></header>
<div class="wrap"><div class="grid2">
  <div class="panel input">
    <h2>명단 · 조건</h2>
    <label>학생 명단 (한 줄에 한 명, 성별은 "이름,남/여")</label>
    <textarea id="roster" placeholder="김민준,남&#10;이서연,여&#10;박도윤"></textarea>
    <div class="toolbar" style="margin-top:8px">
      <button onclick="document.getElementById('file').click()">엑셀/CSV 열기</button>
      <input id="file" type="file" accept=".xlsx,.xlsm,.csv,.txt" style="display:none" onchange="upload(event)">
      <button onclick="loadSample()">예시</button>
    </div>
    <div class="row" style="margin-top:6px"><div><label>행(앞뒤 줄)</label><input id="rows" type="number" value="5" min="1" max="20"></div>
      <div><label>열(좌우)</label><input id="cols" type="number" value="6" min="1" max="20"></div></div>
    <label>배치 방식</label>
    <select id="method"><option value="random">제비뽑기(무작위)</option><option value="gender">남녀 균형(교차)</option><option value="order">입력 순서</option></select>
    <label>인접 금지 (한 줄에 "학생A,학생B")</label>
    <textarea id="separate" style="min-height:60px" placeholder="김민준,이서연"></textarea>
    <div class="toolbar"><button class="btn-primary" onclick="make()">자리 배치 생성</button></div>
  </div>
  <div class="panel">
    <h2>배치 결과</h2>
    <div id="msg"></div>
    <div id="board"><div class="empty-hint">왼쪽에서 명단과 조건을 입력하고 [자리 배치 생성]을 누르세요.</div></div>
    <div class="toolbar no-print" id="resultTools" style="display:none">
      <button onclick="reshuffle()">다시 섞기</button>
      <button class="btn-primary" onclick="exportXlsx()">엑셀 다운로드</button>
      <button onclick="window.print()">인쇄</button>
    </div>
  </div>
</div></div>
<script>
let lastGrid=null, lastCols=0;
function parseRoster(){
  const lines=document.getElementById('roster').value.split('\n');
  const students=[];
  for(const raw of lines){const line=raw.trim();if(!line)continue;
    const parts=line.split(',').map(s=>s.trim());
    let g=null;const gv=parts[1];
    if(gv==='남'||gv==='M'||gv==='m'||gv==='남자')g='M';
    else if(gv==='여'||gv==='F'||gv==='f'||gv==='여자')g='F';
    students.push({name:parts[0],gender:g});
  }
  return students;
}
function parseSeparate(){
  const lines=document.getElementById('separate').value.split('\n');const out=[];
  for(const raw of lines){const line=raw.trim();if(!line)continue;const p=line.split(',').map(s=>s.trim()).filter(Boolean);if(p.length===2)out.push(p);}
  return out;
}
function upload(e){
  const f=e.target.files[0];if(!f)return;
  const fd=new FormData();fd.append('file',f);
  fetch('/api/parse-upload',{method:'POST',body:fd}).then(r=>r.json().then(d=>({ok:r.ok,d}))).then(x=>{
    if(!x.ok){alert('오류: '+(x.d.error?x.d.error.message:''));return;}
    document.getElementById('roster').value=x.d.students.map(s=>s.gender?`${s.name},${s.gender==='M'?'남':'여'}`:s.name).join('\n');
  });
  e.target.value='';
}
function make(seed){
  const students=parseRoster();
  if(!students.length){alert('학생 명단을 입력하세요.');return;}
  const body={students,rows:+document.getElementById('rows').value,cols:+document.getElementById('cols').value,
    method:document.getElementById('method').value,separate:parseSeparate()};
  if(seed!==undefined)body.seed=seed;
  fetch('/api/arrange',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
    .then(r=>r.json().then(d=>({ok:r.ok,d}))).then(x=>{
      if(!x.ok){alert('오류: '+(x.d.error?x.d.error.message:''));return;}
      render(x.d);
    });
}
function reshuffle(){make(Math.floor(Math.random()*1e9));}
function render(d){
  lastGrid=d.grid;lastCols=d.cols;
  let msg='';
  if(d.separateSatisfied===false)msg+='<div class="warn">인접 금지 조건을 완전히 만족하지 못했습니다. 다시 섞기를 시도해 보세요.</div>';
  if(d.unplaced&&d.unplaced.length)msg+='<div class="warn">좌석이 부족해 배치되지 못한 학생: '+d.unplaced.join(', ')+'</div>';
  if(!msg)msg='<div class="ok2">'+d.count+'명 배치 완료. 교탁을 바라보는 방향 기준입니다.</div>';
  document.getElementById('msg').innerHTML=msg;
  let h='<div class="board"><div class="teacher">교 탁 · 칠판</div><table class="seats"><tbody>';
  for(const row of d.grid){h+='<tr>';
    for(const cell of row){
      if(!cell)h+='<td><div class="seat empty">·</div></td>';
      else{const cls=cell.gender==='M'?'m':(cell.gender==='F'?'f':'');h+='<td><div class="seat '+cls+'">'+esc(cell.name)+'</div></td>';}
    }
    h+='</tr>';}
  h+='</tbody></table></div>';
  document.getElementById('board').innerHTML=h;
  document.getElementById('resultTools').style.display='flex';
}
function exportXlsx(){
  if(!lastGrid){alert('먼저 배치를 생성하세요.');return;}
  const names=lastGrid.map(row=>row.map(c=>c?c.name:null));
  fetch('/api/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:'자리 배치표',grid:names})})
    .then(r=>{if(!r.ok)throw new Error();return r.blob();})
    .then(b=>{const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download='자리배치표.xlsx';a.click();})
    .catch(()=>alert('엑셀 생성에 실패했습니다.'));
}
function esc(s){return (''+s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));}
function loadSample(){
  document.getElementById('roster').value=['김민준,남','이서연,여','박도윤,남','최지우,여','정하준,남','강서윤,여','조은우,남','윤채원,여','임시윤,남','한지민,여','오현우,남','신유나,여'].join('\n');
  document.getElementById('separate').value='김민준,박도윤';
}
</script></body></html>"""
