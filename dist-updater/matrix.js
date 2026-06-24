import{a as i}from"./chunk-NQKLVE2E.js";import c from"fs";import d from"path";var p=r=>`| HTTP Protocol | URL | Auto-update Frequence | Latest Updated | IDC | Provider |
| ------------- | --- | --------------------- | --- | --- | -------- |
${r}
`,u=r=>new Promise(async(s,a)=>{try{let e=await fetch(`${r}/channels.json`);if(/^[2]/.test(e.status.toString())){let n=JSON.parse(await e.text());s(new Date(n.updated_at).toString())}else a(`Get Updated Failed: **${e.statusText}**`)}catch(e){a(`Get Updated Failed: **${e.toString()}**`)}}),m=async()=>{var n;let r=d.resolve("m3u","README.md"),s=await Promise.allSettled((n=i)==null?void 0:n.map(async t=>{let o="";try{o=await u(t.url)}catch(l){o=l}finally{return`| ${t.protocol} | <${t.url}> | ${t.frequence} | ${o} | ${t.idc} | ${t.provider} |`}})),a=p(s.map(t=>t.value).join(`
`)),e=c.readFileSync(r,"utf8").toString();c.writeFileSync(r,e.replace("<!-- matrix_here -->",a))};m();
