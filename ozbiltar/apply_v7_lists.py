from pathlib import Path
import re, json
root=Path('.')
idx=root/'index.html'
app=root/'js/app.js'
css=root/'assets/styles.css'
config=root/'js/config.js'
build=root/'android-native/app/build.gradle'

s=idx.read_text(encoding='utf-8')
if 'data-view="lists"' not in s:
    s=s.replace('''        <span class="sidebar-label">OPERASYON</span>\n        <button class="nav-btn" data-view="records"><span>▦</span> Kayıt Merkezi</button>''','''        <span class="sidebar-label">OPERASYON</span>\n        <button class="nav-btn" data-view="lists"><span>☷</span> Listeler</button>\n        <button class="nav-btn" data-view="records"><span>▦</span> Kayıt Merkezi</button>''')
if 'id="dashboardListCards"' not in s:
    s=s.replace('''        </article>\n\n        <div class="dashboard-grid">\n          <article class="panel executive-panel">''','''        </article>\n\n        <article class="panel quick-lists-panel">\n          <div class="panel-head">\n            <div><span class="eyebrow">KAYIT LİSTELERİ</span><h2>İşletme kayıtlarını aç</h2></div>\n            <button class="link-btn" data-go="lists">Tüm listeler →</button>\n          </div>\n          <div id="dashboardListCards" class="list-hub-grid"></div>\n        </article>\n\n        <div class="dashboard-grid">\n          <article class="panel executive-panel">''',1)
if 'id="listsView"' not in s:
    s=s.replace('''      <section id="recordsView" class="view">''','''      <section id="listsView" class="view">\n        <div class="page-head">\n          <div><span class="eyebrow">OPERASYON LİSTELERİ</span><h1>Listeler</h1><p>Hayvanlar, stoklar, sağlık, yem, finans ve diğer kayıtları ayrı ayrı görüntüle.</p></div>\n        </div>\n        <div id="listHubGrid" class="list-hub-grid list-hub-full"></div>\n        <article class="panel list-preview-panel">\n          <div class="panel-head">\n            <div><span class="eyebrow">SEÇİLİ LİSTE</span><h2 id="listPreviewTitle">Hayvan Listesi</h2></div>\n            <div class="head-actions"><button id="listAddBtn" class="secondary-btn">+ Kayıt Ekle</button><button id="listOpenRecordsBtn" class="primary-btn">Tümünü Yönet</button></div>\n          </div>\n          <div id="listPreview" class="business-list"></div>\n        </article>\n      </section>\n\n      <section id="recordsView" class="view">''',1)
idx.write_text(s,encoding='utf-8')

s=app.read_text(encoding='utf-8')
s=s.replace('''  let currentModule = "animals";\n  let currentAgents = [];''','''  let currentModule = "animals";\n  let selectedListModule = "animals";\n  let currentAgents = [];''')
if 'const LIST_MODULES' not in s:
    marker='  function renderDashboard(){'
    code=r'''  const LIST_MODULES = [
    ["animals","Hayvan Listesi","Sürüdeki tüm hayvanlar","🐄"],
    ["stock","Stok Listesi","Yem, ilaç ve sarf stokları","▣"],
    ["health","Sağlık Listesi","Açık ve kapanan sağlık kayıtları","✚"],
    ["feed","Yem / Rasyon Listesi","Rasyon ve tüketim kayıtları","◒"],
    ["milk","Süt Üretim Listesi","Günlük süt kayıtları","🥛"],
    ["finance","Finans Listesi","Gelir ve gider hareketleri","₺"],
    ["staff","Personel Listesi","Aktif personel ve görevler","👤"],
    ["reproduction","Üreme Listesi","Tohumlama, gebelik ve doğum","◉"],
    ["purchase","Satın Alma Listesi","Tedarik ve ödeme kayıtları","↙"],
    ["sales","Satış Listesi","Müşteri ve satış kayıtları","↗"],
    ["maintenance","Bakım Listesi","Ekipman ve bakım kayıtları","⚙"],
    ["risk","Risk Listesi","Açık risk ve kontroller","!"],
    ["marketing","Pazarlama Listesi","Pazar fırsatları","◎"],
    ["advertising","Reklam Listesi","Kampanya performansı","◈"],
    ["rnd","Ar-Ge Listesi","Deneme ve geliştirme kayıtları","⌁"],
    ["investment","Yatırım Listesi","Yatırım ve geri dönüş kayıtları","◆"]
  ];
  function listMeta(k){return LIST_MODULES.find(x=>x[0]===k)||[k,SCHEMAS[k]?.name||k,"Kayıtlar","▦"]}
  function listCardStatus(k){
    const rows=state.data[k]||[];
    if(k==="animals") return `${rows.filter(r=>r.status==="Sağmal").length} sağmal`;
    if(k==="stock") {const known=rows.filter(r=>String(r.inQty??"").trim()!==""||String(r.outQty??"").trim()!==""); const critical=known.filter(r=>stockStatus(r)==="KRİTİK").length; return known.length?`${critical} kritik · ${rows.length-known.length} miktar belirsiz`:`${rows.length} ürün · miktarlar eksik`;}
    if(k==="health") return `${rows.filter(r=>r.status==="Açık").length} açık`;
    if(k==="finance") return `${rows.filter(r=>r.type==="Gelir").length} gelir · ${rows.filter(r=>r.type==="Gider").length} gider`;
    if(k==="staff") return `${rows.filter(r=>r.status!=="Ayrıldı").length} aktif`;
    if(k==="milk") return `${rows.length} günlük kayıt`;
    return `${rows.length} kayıt`;
  }
  function listHubCard(k,title,desc,icon){const count=(state.data[k]||[]).length;return `<button class="list-hub-card ${selectedListModule===k?"selected":""}" data-list-module="${k}"><span class="list-hub-icon">${icon}</span><span class="list-hub-copy"><strong>${escapeHtml(title)}</strong><small>${escapeHtml(desc)}</small><em>${count} kayıt · ${escapeHtml(listCardStatus(k))}</em></span><span class="list-hub-arrow">›</span></button>`}
  function renderDashboardLists(){if($("dashboardListCards")) $("dashboardListCards").innerHTML=LIST_MODULES.slice(0,8).map(v=>listHubCard(...v)).join("");bindListHubButtons()}
  function recordIdentity(k,r){
    const safe=v=>String(v??"").trim();
    if(k==="animals") return {title:`Küpe ${safe(r.earTag)||"—"}${safe(r.name)?" · "+safe(r.name):""}`,subtitle:[safe(r.breed),safe(r.status),safe(r.sex),safe(r.pregnancy)].filter(Boolean).join(" · ")||"Hayvan kaydı",tone:r.status==="Sağmal"?"green":"blue"};
    if(k==="stock"){const known=safe(r.inQty)!==""||safe(r.outQty)!=="";const current=known?N(r.inQty)-N(r.outQty):null;const st=known?stockStatus(r):"BELİRSİZ";return {title:safe(r.product)||`Stok ${safe(r.code)}`,subtitle:known?`Mevcut ${num(current,2)} ${safe(r.unit)} · Min ${safe(r.minStock)||"—"}`:`Miktar bilinmiyor · Min ${safe(r.minStock)||"—"}`,tone:st==="KRİTİK"?"red":st==="BELİRSİZ"?"amber":"green"};}
    if(k==="health") return {title:`Küpe ${safe(r.earTag)||"—"} · ${safe(r.diagnosis)||"Sağlık kaydı"}`,subtitle:[fmtDate(r.date),safe(r.status),safe(r.priority)].filter(Boolean).join(" · "),tone:r.status==="Açık"?(r.priority==="Kritik"?"red":"amber"):"green"};
    if(k==="feed") return {title:safe(r.ration)||"Rasyon",subtitle:`${fmtDate(r.date)} · ${safe(r.amountKg)||"—"} kg · ${safe(r.group)||"Grup yok"}`,tone:"blue"};
    if(k==="milk") return {title:`${safe(r.liters)||"—"} L süt`,subtitle:`${fmtDate(r.date)} · ${safe(r.recordType)||"Kayıt"}`,tone:"blue"};
    if(k==="finance") return {title:`${safe(r.type)||"Finans"} · ${money(r.amount)}`,subtitle:[fmtDate(r.date),safe(r.category),safe(r.description)].filter(Boolean).join(" · "),tone:r.type==="Gelir"?"green":"red"};
    if(k==="staff") return {title:safe(r.name)||"Personel",subtitle:[safe(r.role),safe(r.status)].filter(Boolean).join(" · "),tone:r.status==="Aktif"?"green":"blue"};
    if(k==="reproduction") return {title:`Küpe ${safe(r.earTag)||"—"} · ${safe(r.action)||"Üreme"}`,subtitle:[fmtDate(r.date),safe(r.status),safe(r.result)].filter(Boolean).join(" · "),tone:"blue"};
    if(k==="sales") return {title:`${safe(r.product)||"Satış"} · ${money(N(r.quantity)*N(r.unitPrice))}`,subtitle:[fmtDate(r.date),safe(r.customer),safe(r.paymentStatus)].filter(Boolean).join(" · "),tone:r.paymentStatus==="Tahsil Edildi"?"green":"amber"};
    if(k==="purchase") return {title:`${safe(r.item)||"Satın alma"} · ${money(N(r.quantity)*N(r.unitPrice))}`,subtitle:[fmtDate(r.date),safe(r.supplier),safe(r.paymentStatus)].filter(Boolean).join(" · "),tone:r.paymentStatus==="Ödendi"?"green":"amber"};
    if(k==="maintenance") return {title:safe(r.equipment)||"Ekipman",subtitle:[safe(r.category),maintenanceStatus(r),safe(r.service)].filter(Boolean).join(" · "),tone:maintenanceStatus(r)==="GECİKMİŞ"?"red":"blue"};
    if(k==="risk"){const score=N(r.probability)*N(r.impact);return {title:safe(r.risk)||"Risk",subtitle:`Puan ${score} · ${safe(r.status)||"—"}`,tone:score>=15?"red":score>=8?"amber":"blue"};}
    if(k==="marketing") return {title:safe(r.opportunity)||"Pazar fırsatı",subtitle:[safe(r.market),safe(r.segment),safe(r.stage)].filter(Boolean).join(" · "),tone:r.stage==="Kazanıldı"?"green":"blue"};
    if(k==="advertising") return {title:safe(r.campaign)||"Kampanya",subtitle:[safe(r.channel),safe(r.status),`Ciro ${money(r.revenue)}`].filter(Boolean).join(" · "),tone:r.status==="Yayında"?"green":"blue"};
    if(k==="rnd") return {title:safe(r.project)||"Ar-Ge",subtitle:[safe(r.kpi),safe(r.decision)].filter(Boolean).join(" · "),tone:r.decision==="Başarılı"?"green":"blue"};
    if(k==="investment") return {title:safe(r.opportunity)||"Yatırım",subtitle:[money(r.amount),safe(r.risk),safe(r.status)].filter(Boolean).join(" · "),tone:r.risk==="Yüksek"?"red":"blue"};
    return {title:SCHEMAS[k]?.name||"Kayıt",subtitle:"Kayıt",tone:"blue"};
  }
  function businessListItem(k,r){const m=recordIdentity(k,r);return `<article class="business-list-item"><div class="business-list-main"><span class="business-status-dot ${m.tone}"></span><div><strong>${escapeHtml(m.title)}</strong><small>${escapeHtml(m.subtitle)}</small></div></div><div class="business-list-actions"><button class="action-btn list-edit" data-id="${r.id}" data-module="${k}">Düzenle</button><button class="icon-list-btn list-delete" data-id="${r.id}" data-module="${k}">×</button></div></article>`}
  function renderListPreview(){const meta=listMeta(selectedListModule),rows=(state.data[selectedListModule]||[]).slice().reverse();if($("listPreviewTitle")) $("listPreviewTitle").textContent=`${meta[1]} · ${rows.length}`;if($("listPreview")) $("listPreview").innerHTML=rows.length?rows.slice(0,60).map(r=>businessListItem(selectedListModule,r)).join(""):`<div class="empty">${escapeHtml(meta[1])} boş. <strong>+ Kayıt Ekle</strong> ile başlayın.</div>`;document.querySelectorAll(".list-edit").forEach(b=>b.onclick=()=>openRecordModal(b.dataset.id,b.dataset.module));document.querySelectorAll(".list-delete").forEach(b=>b.onclick=()=>{if(confirm("Bu kayıt silinsin mi?")){const k=b.dataset.module;state.data[k]=state.data[k].filter(x=>x.id!==b.dataset.id);save(`${SCHEMAS[k].name} kaydı silindi`);renderLists()}})}
  function bindListHubButtons(){document.querySelectorAll("[data-list-module]").forEach(b=>b.onclick=()=>{selectedListModule=b.dataset.listModule;switchView("lists");renderLists()})}
  function renderLists(){if($("listHubGrid")) $("listHubGrid").innerHTML=LIST_MODULES.map(v=>listHubCard(...v)).join("");bindListHubButtons();renderListPreview()}

'''
    s=s.replace(marker,code+marker,1)
end='''    $("agentStrip").innerHTML=runAgents().map(a=>`<div class="agent-chip"><strong>${a.name}</strong><span>${a.status==="active"?"Aktif":a.status==="learning"?"Veri topluyor":"Yetersiz veri"} · Güven %${a.confidence}</span></div>`).join("");\n  }'''
if end in s and 'renderDashboardLists();' not in s[s.index(end):s.index(end)+len(end)+50]:
    s=s.replace(end,end.replace('\n  }','\n    renderDashboardLists();\n  }'),1)
old='''      <div class="mobile-record-list">${reversed.map((r,idx)=>`<article class="record-mobile-card">\n        <div class="record-mobile-title"><strong>${escapeHtml(schema.name)} #${rows.length-idx}</strong><span class="badge blue">Kayıt</span></div>'''
new='''      <div class="mobile-record-list">${reversed.map((r,idx)=>{const ident=recordIdentity(currentModule,r);return `<article class="record-mobile-card">\n        <div class="record-mobile-title"><div><strong>${escapeHtml(ident.title)}</strong><small class="record-card-subtitle">${escapeHtml(ident.subtitle)}</small></div><span class="badge ${ident.tone}">${escapeHtml(schema.name)}</span></div>'''
if old in s:
    s=s.replace(old,new,1)
    s=s.replace('''      </article>`).join("")}</div>`:''','''      </article>`}).join("")}</div>`:''',1)
s=s.replace('renderDashboard();renderAgents();renderApprovals();renderAlerts();renderRecords();renderTasks();renderDecisions();renderGoals();renderSettings();','renderDashboard();renderAgents();renderApprovals();renderAlerts();renderLists();renderRecords();renderTasks();renderDecisions();renderGoals();renderSettings();')
if '$("listAddBtn").onclick' not in s:
    s=s.replace('''  $("newTaskBtn").onclick=openTaskModal;''','''  $("listAddBtn").onclick=()=>openRecordModal(null,selectedListModule);\n  $("listOpenRecordsBtn").onclick=()=>{currentModule=selectedListModule;switchView("records");renderRecords();};\n  $("newTaskBtn").onclick=openTaskModal;''')
app.write_text(s,encoding='utf-8')

c=css.read_text(encoding='utf-8')
if 'V7.1 — PROFESSIONAL MOBILE LISTS' not in c:
    c += r'''
/* V7.1 — PROFESSIONAL MOBILE LISTS */
.quick-lists-panel{margin-bottom:14px}.list-hub-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.list-hub-full{grid-template-columns:repeat(4,minmax(0,1fr));margin-bottom:14px}.list-hub-card{appearance:none;width:100%;min-width:0;border:1px solid var(--line);border-radius:14px;background:#fff;padding:12px;display:grid;grid-template-columns:42px minmax(0,1fr) 18px;gap:10px;align-items:center;text-align:left;color:var(--text);box-shadow:0 4px 16px rgba(18,32,51,.045)}.list-hub-card:hover,.list-hub-card.selected{border-color:#c6a371;background:linear-gradient(180deg,#fff,#fffaf2)}.list-hub-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center;background:var(--surface-soft);color:var(--navy);font-size:19px;font-weight:900}.list-hub-copy{min-width:0}.list-hub-copy strong,.list-hub-copy small,.list-hub-copy em{display:block;overflow:hidden;text-overflow:ellipsis}.list-hub-copy strong{color:var(--navy);font-size:13px;white-space:nowrap}.list-hub-copy small{color:var(--muted);font-size:10px;white-space:nowrap;margin-top:2px}.list-hub-copy em{color:var(--bronze);font-size:10px;font-style:normal;font-weight:800;white-space:nowrap;margin-top:5px}.list-hub-arrow{color:#9aa5b3;font-size:24px;line-height:1}.business-list{display:grid;gap:8px}.business-list-item{border:1px solid var(--line);border-radius:13px;background:#fff;padding:11px;display:flex;align-items:center;justify-content:space-between;gap:10px}.business-list-main{display:flex;align-items:center;gap:10px;min-width:0;flex:1}.business-list-main>div{min-width:0}.business-list-main strong,.business-list-main small{display:block;overflow:hidden;text-overflow:ellipsis}.business-list-main strong{color:var(--navy);font-size:13px;white-space:nowrap}.business-list-main small{color:var(--muted);font-size:10px;margin-top:3px;white-space:nowrap}.business-status-dot{flex:0 0 auto;width:10px;height:10px;border-radius:50%;background:#9ca6b5}.business-status-dot.green{background:var(--green)}.business-status-dot.red{background:var(--red)}.business-status-dot.amber{background:var(--amber)}.business-status-dot.blue{background:#6882a4}.business-list-actions{display:flex;align-items:center;gap:5px;flex:0 0 auto}.icon-list-btn{width:38px;height:38px;border:1px solid var(--line);border-radius:9px;background:#fff;color:var(--red);font-size:20px;line-height:1}.record-card-subtitle{display:block;color:var(--muted);font-size:10px;margin-top:3px;max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}@media(max-width:1100px){.list-hub-grid,.list-hub-full{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.list-hub-grid,.list-hub-full{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.list-hub-card{grid-template-columns:36px minmax(0,1fr) 12px;padding:10px 9px;gap:7px;border-radius:13px;min-height:82px}.list-hub-icon{width:36px;height:36px;border-radius:10px;font-size:17px}.list-hub-copy strong{font-size:12px}.list-hub-copy small{display:none}.list-hub-copy em{font-size:9px;white-space:normal;line-height:1.25;max-height:24px}.list-hub-arrow{font-size:20px}.business-list-item{align-items:flex-start;padding:11px}.business-list-main strong{font-size:13px;white-space:normal;line-height:1.3}.business-list-main small{white-space:normal;line-height:1.3;max-height:30px}.business-list-actions{flex-direction:column}.business-list-actions .action-btn{margin:0;min-height:38px;font-size:11px}.icon-list-btn{width:100%;height:32px;font-size:17px}.list-preview-panel .head-actions{width:100%}.list-preview-panel .head-actions button{flex:1}}
'''
css.write_text(c,encoding='utf-8')

c=config.read_text(encoding='utf-8').replace('version: "6.0.0"','version: "7.1.0"')
config.write_text(c,encoding='utf-8')
if build.exists():
    b=build.read_text(encoding='utf-8'); b=re.sub(r'versionCode\s+\d+','versionCode 71',b); b=re.sub(r'versionName\s+"[^"]+"','versionName "7.1.0"',b); build.write_text(b,encoding='utf-8')
print('V7.1 lists patch applied')
