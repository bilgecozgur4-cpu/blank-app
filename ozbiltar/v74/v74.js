window.OZ_V74 = (()=>{
  const N=v=>Number.isFinite(Number(v))?Number(v):0;
  const roles={
    "Yönetici":{views:"*",write:"*",label:"Tam Yetki"},
    "Muhasebe":{views:["dashboard","lists","records","tasks","calendar","financeCenter","monthlyClose","reports","backup","attachments","cloud"],write:["finance","sales","purchase","stock"],label:"Finans & Kayıt"},
    "Çalışan":{views:["dashboard","lists","records","tasks","calendar","attachments","qr","herdPerformance"],write:["milk","feed","stock","animals"],label:"Operasyon"},
    "Veteriner":{views:["dashboard","lists","records","calendar","attachments","qr","herdPerformance","profitability"],write:["animals","health","reproduction"],label:"Sürü Sağlığı"},
    "Gözlemci":{views:["dashboard","lists","reports","calendar","herdPerformance"],write:[],label:"Salt Okunur"}
  };
  function activeUser(state){return (state.users||[]).find(u=>u.id===state.activeUserId&&u.active!==false)||(state.users||[])[0]||{id:"owner",name:"Yönetici",role:"Yönetici"}}
  function rule(state){return roles[activeUser(state).role]||roles["Gözlemci"]}
  function canView(state,view){const r=rule(state);return r.views==="*"||r.views.includes(view)}
  function canWriteModule(state,module){const r=rule(state);return r.write==="*"||r.write.includes(module)}
  async function pinHash(pin){const data=new TextEncoder().encode("OZBILTAR|"+String(pin));const b=await crypto.subtle.digest("SHA-256",data);return [...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,"0")).join("")}
  function daysAgo(n){const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-n);return d}
  function completeness(a){const keys=["earTag","birthDate","breed","sex","status","pregnancy"],have=keys.filter(k=>String(a[k]??"").trim()!=="").length;return Math.round(have/keys.length*100)}
  function herdPerformance(state){
    const animals=(state.data.animals||[]).filter(a=>!["Satıldı","Öldü"].includes(a.status));
    const milk=(state.data.milk||[]).filter(m=>m.recordType==="Hayvan Bazlı"&&m.date&&new Date(m.date+"T00:00:00")>=daysAgo(30));
    const recent7=daysAgo(6),prev14=daysAgo(13),prev7=daysAgo(7),healthCut=daysAgo(90);
    const per=animals.map(a=>{
      const rows=milk.filter(m=>String(m.earTag)===String(a.earTag));
      const liters=rows.reduce((s,r)=>s+N(r.liters),0), count=rows.length;
      const r7=rows.filter(r=>new Date(r.date+"T00:00:00")>=recent7),p7=rows.filter(r=>{const d=new Date(r.date+"T00:00:00");return d>=prev14&&d<prev7});
      const avg7=r7.length?r7.reduce((s,r)=>s+N(r.liters),0)/r7.length:0,avgPrev=p7.length?p7.reduce((s,r)=>s+N(r.liters),0)/p7.length:0;
      const trend=avgPrev>0?(avg7-avgPrev)/avgPrev*100:null;
      const health=(state.data.health||[]).filter(h=>String(h.earTag)===String(a.earTag)&&h.date&&new Date(h.date+"T00:00:00")>=healthCut).length;
      return {animal:a,liters,count,avg:count?liters/count:0,trend,health,quality:completeness(a),known:count>=3};
    });
    const known=per.filter(x=>x.known&&x.avg>0), herdAvg=known.length?known.reduce((s,x)=>s+x.avg,0)/known.length:0;
    per.forEach(x=>{if(!x.known){x.score=null;return}const milkScore=herdAvg?Math.max(5,Math.min(45,25*(x.avg/herdAvg))):20;const healthScore=Math.max(0,25-x.health*6);const dataScore=x.quality*.20;const reproScore=String(x.animal.pregnancy||"").trim()?10:5;x.score=Math.round(Math.min(100,milkScore+healthScore+dataScore+reproScore));});
    return {rows:per.sort((a,b)=>(b.score??-1)-(a.score??-1)),herdAvg,known:known.length,total:animals.length,coverage:animals.length?Math.round(known.length/animals.length*100):0};
  }
  function cloudConfig(){try{return JSON.parse(localStorage.getItem("ozbiltar_v74_cloud")||"{}")||{}}catch{return {}}}
  function saveCloudConfig(c){localStorage.setItem("ozbiltar_v74_cloud",JSON.stringify(c))}
  return {roles,activeUser,rule,canView,canWriteModule,pinHash,herdPerformance,cloudConfig,saveCloudConfig};
})();