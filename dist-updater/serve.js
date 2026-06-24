import a from"fs";import o from"path";import y from"koa";import{default as g}from"koa-static";import k from"koa-router";import w from"markdown-it";import f from"path";import h from"fs";import"dotenv/config";var d=t=>{let e=f.resolve("m3u",t);return h.existsSync(e)},i=async()=>{let{ENABLE_IPTV_CHECKER:t,IPTV_CHECKER_URL:e}=process.env;if(t!=="true"||!e)return!1;try{let r=await fetch(e);return!!/^[2]/.test(r.status.toString())}catch{return!1}};var c=new y,n=new k,b=new w({html:!0}),p=(t,e)=>{let r="";return a.existsSync(t)?r=a.readFileSync(t).toString():r=a.readFileSync(e).toString(),`
    <html lang="en">
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.0/github-markdown.css" integrity="sha512-LX/J+iRwkfRqaipVsfmi2B1S7xrqXNHdTb6o4tWe2Ex+//EN3ifknyLIbX5f+kC31zEKHon5l9HDEwTQR1H8cg==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <style>
        html, body {
            margin: 0;
            padding: 0;
        }
        .markdown-body {
            padding: 50px 100px;
        }

        tr, td {
            color: var(--color-fg-default);
        }
    </style>
    </head>
    <body>
        <div class="markdown-body">${b.render(r)}</div>
    </body>
    </html>
    `};c.use(g("./m3u"));n.get("/",t=>{let e=o.resolve("m3u","README.md"),r=o.resolve("back","README.md");t.body=p(e,r)});n.get("/list/:channel",t=>{let e=t.params.channel,r=o.resolve("m3u","list",`${e}.md`),s=o.resolve("back","list",`${e}.md`);t.body=p(r,s)});n.get("/check/:channel",async t=>{let e=t.params.channel;if(!d(`${e}.m3u`)){t.status=404;return}if(!await i()){t.status=403;return}t.body=a.readFileSync(o.resolve("public","check.html")).toString()});n.get("/api/check",async t=>{if(!await i()){t.status=403;return}let{url:e,timeout:r}=t.query;if(!e){t.status=403;return}try{let s=parseInt(r,10),l=await fetch(`${process.env.IPTV_CHECKER_URL}/check/url-is-available?url=${e}&timeout=${isNaN(s)?-1:s}`);t.status=l.status,t.body=await l.text()}catch{t.status=500;return}});c.use(n.routes());c.listen(8080,()=>{console.log("Serving at http://127.0.0.1:8080"),console.log("If the network supports ipv6, visit http://[::1]:8080")});
