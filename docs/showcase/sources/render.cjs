#!/usr/bin/env node
const fs=require('fs'), path=require('path'), {execFileSync}=require('child_process');
const sharp=require('sharp');
const root=path.resolve(__dirname,'..'), repo=path.resolve(root,'../..');
process.env.PLEKTRA_FRAMES_DIR=path.join(root,'frames');
const catalog=JSON.parse(fs.readFileSync(path.join(__dirname,'catalog.json')));
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
const uri=p=>'data:image/'+(p.endsWith('.svg')?'svg+xml':'png')+';base64,'+fs.readFileSync(p).toString('base64');
const text=(s,x,y,size=28,color='#555A60',weight=400,anchor='start')=>`<text x="${x}" y="${y}" font-family="Arial, sans-serif" font-size="${size}" font-weight="${weight}" fill="${color}" text-anchor="${anchor}">${esc(s)}</text>`;
const image=(p,x,y,w,h)=>`<image href="${uri(p)}" x="${x}" y="${y}" width="${w}" height="${h}" preserveAspectRatio="xMidYMid meet"/>`;
const metas={},assets=new Set();
const digest=(...paths)=>{const hash=require('crypto').createHash('sha256');paths.forEach(p=>hash.update(fs.readFileSync(p)));return hash.digest('hex').slice(0,10);};
async function dimensions(p){return metas[p]||(metas[p]=await sharp(p).metadata());}
async function fit(p,x,y,w,h){let m=await dimensions(p),scale=Math.min(w/m.width,h/m.height);let rw=m.width*scale,rh=m.height*scale;return image(p,x+(w-rw)/2,y+(h-rh)/2,rw,rh);}
function raw(item){return path.join(root,'raw',item[0],item[1]+'.png');}
async function frame(item){const stamp=digest(raw(item),path.join(process.env.PLEKTRA_FRAMES_DIR,item[0]+'-frame.png'),path.join(process.env.PLEKTRA_FRAMES_DIR,item[0]+'-screen-mask.png'),path.join(process.env.PLEKTRA_FRAMES_DIR,item[0]+'-geometry.json'));const out=path.join(root,'composed',item[0]+'-'+item[1]+'-'+stamp+'.png');assets.add(out);if(!fs.existsSync(out))execFileSync(process.execPath,[path.join(__dirname,'compose-screen.cjs'),item[0],raw(item),out]);return out;}
async function crop(item,mode='bar'){
 const stamp=digest(raw(item),__filename,path.join(root,'raw/titan-2-elite/r5-config-hidden.png'));const out=path.join(root,'crops',item[0]+'-'+item[1]+'-'+mode+'-polished-'+stamp+'.png');assets.add(out);if(fs.existsSync(out))return out;
 const src=raw(item),m=await dimensions(src),pixel=item[0]==='pixel-7a';let top,height;
 if(mode==='snippet-result'){
  top=340;height=item[1].endsWith('address')?130:78;await sharp(src).extract({left:45,top,width:m.width-90,height}).png().toFile(out);return out;
 }
 if(mode==='snippet'){
  top=996;height=81;await sharp(src).extract({left:Math.round(m.width/3),top,width:Math.floor(m.width/3),height}).png().toFile(out);return out;
 }
 if(mode==='context'){top=Math.round(m.height*(pixel?.10:.16));height=Math.round(m.height*(pixel?.22:.26));}
 else if(mode==='layout'){top=1672;height=448;}
 else if(mode==='modifier'){height=Math.round(m.height*.10);top=m.height-height;}
 else if(pixel && item[1].startsWith('clipboard')){top=1560;height=288;}
 else if(pixel){top=1372;height=m.height-top;}
 else if(item[0]==='titan-2-elite'&&(/^r[23456]-/).test(item[1])){
  height=item[1].includes('config-leds')||item[1].includes('slim')?123:item[1].includes('config-suggestions')||item[1].includes('shared')?103:204;top=m.height-height;
 }else{height=Math.round(m.height*(item[1].includes('symbols')?.35:.19));top=m.height-height;}
 let extracted=await sharp(src).extract({left:0,top,width:m.width,height}).png().toBuffer();
 if(item[0]==='titan-2-elite'&&mode==='bar'&&(/config-|slim|transparent-dark/).test(item[1])){
  const {data,info}=await sharp(extracted).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const reference=await sharp(path.join(root,'raw/titan-2-elite/r5-config-hidden.png')).extract({left:0,top,width:m.width,height}).ensureAlpha().raw().toBuffer();
  for(let i=0;i<data.length;i+=4){if(Math.max(...[0,1,2].map(c=>Math.abs(data[i+c]-reference[i+c])))<=1)data[i+3]=0;}
  extracted=await sharp(data,{raw:{width:info.width,height:info.height,channels:4}}).png().toBuffer();
 }

 if(item[0]==='titan-2-elite'&&mode==='bar'&&!(/config-|slim|transparent-dark/).test(item[1])){
  const {data,info}=await sharp(extracted).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const bg=[data[((height-1)*info.width)*4],data[((height-1)*info.width)*4+1],data[((height-1)*info.width)*4+2]],radius=Math.min(123,height);
  // Remove only background outside the lower contour. Every native LED/glyph pixel survives.
  for(let y=height-radius;y<height;y++)for(let x=0;x<info.width;x++){
   const cx=x<radius?radius:x>=info.width-radius?info.width-radius:null;
   if(cx===null||Math.hypot(x-cx,y-(height-radius))<=radius)continue;
   const i=(y*info.width+x)*4;
   if([0,1,2].every(c=>Math.abs(data[i+c]-bg[c])<=1))data[i+3]=0;
  }
  extracted=await sharp(data,{raw:{width:info.width,height:info.height,channels:4}}).png().toBuffer();
 }
 await sharp(extracted).png().toFile(out);return out;
}
async function contents(card,box,portrait){
 let [x,y,w,h]=box, out='';
 if(card.layout==='devices'||card.layout==='pair'){
   const n=card.items.length,gap=portrait?28:32,cell=(w-gap*(n-1))/n;
   for(let i=0;i<n;i++){const p=await frame(card.items[i]);out+=await fit(p,x+i*(cell+gap),y,cell,h-55);out+=text(card.items[i][2],x+i*(cell+gap)+cell/2,y+h-12,portrait?25:26,'#565B60',400,'middle');}
 }else if(card.layout==='frame-crop'){
   const item=card.items[0],p=await frame(item),cp=await crop(item,card.id==='08-snippet-selection'?'snippet':card.id==='13-selection'?'context':'bar');
   if(portrait){out+=await fit(p,x+110,y,w-220,h*.64);out+=text(item[2],x,y+h*.71,27);out+=await fit(cp,x,y+h*.73,w,h*.27);}
   else{out+=await fit(p,x,y,w*.44,h);let cm=await dimensions(cp),ih=Math.min(h*.58,w*.51*cm.height/cm.width),iy=y+(h-ih)/2;out+=text(item[2],x+w*.49,iy-28,26);out+=await fit(cp,x+w*.49,iy,w*.51,ih);}
 }else if(card.layout==='bar-configs'){
   const total=card.items.slice(0,3).reduce((sum,i)=>sum+(i[1].endsWith('full')?204:i[1].endsWith('leds')?123:103),0);
   const iw=Math.min(w,(h-4*74-30)*1076/total),scale=iw/1076;let yy=y;
   for(const item of card.items){out+=text(item[2],x,yy+27,27);yy+=44;if(item[1].endsWith('hidden')){out+=text('Look, no bar.',x,yy+29,30,'#565B60');yy+=58;}else{const cp=await crop(item),cm=await dimensions(cp);out+=image(cp,x,yy,iw,cm.height*scale);yy+=cm.height*scale+25;}}
 }else if(card.layout==='snippets'){
   let yy=y+25;for(const item of card.items){out+=text(item[2],x,yy+27,27);yy+=50;const cp=await crop(item,'snippet-result'),cm=await dimensions(cp),iw=w,ih=iw*cm.height/cm.width;out+=image(cp,x,yy,iw,ih);yy+=ih+35;}
 }else if(card.layout==='compare-layouts'){
   const gap=60,ch=(h-gap)/2;for(let i=0;i<card.items.length;i++){const cp=await crop(card.items[i],'layout');out+=text(card.items[i][2],x,y+i*(ch+gap)+28,28);out+=await fit(cp,x,y+i*(ch+gap)+48,w,ch-48);}
 }else if(card.layout==='compare'){
   const files=await Promise.all(card.items.map(i=>crop(i,card.id==='14-modifiers'?'modifier':'bar')));const ratio=await Promise.all(files.map(async p=>{let m=await dimensions(p);return m.height/m.width;}));
   const total=ratio.reduce((s,r)=>s+w*r,0)+card.items.length*42+(card.items.length-1)*24;
   const scale=Math.min(1,h/total),iw=w*scale;let yy=y+(h-total*scale)/2;
   for(let i=0;i<files.length;i++){out+=text(card.items[i][2],x,yy+28*scale,portrait?28:27);yy+=42*scale;let ih=iw*ratio[i];out+=image(files[i],x+(w-iw)/2,yy,iw,ih);yy+=ih+24*scale;}
 }else if(card.layout==='gallery'){
   const n=card.items.length,cols=n>6?3:2,rows=Math.ceil(n/cols),gap=24,cw=(w-gap*(cols-1))/cols,ch=Math.min((h-gap*(rows-1))/rows,portrait?315:260);
   let yy=y+(h-(ch*rows+gap*(rows-1)))/2;
   for(let i=0;i<n;i++){const p=await crop(card.items[i],card.id==='17-layouts'?'layout':'bar'),xx=x+(i%cols)*(cw+gap),ty=yy+Math.floor(i/cols)*(ch+gap);out+=text(card.items[i][2],xx,ty+27,portrait?25:26);out+=await fit(p,xx,ty+44,cw,ch-44);}
 }
 return out;
}
(async()=>{
 for(const folder of ['composed','crops','exports','previews'])fs.mkdirSync(path.join(root,folder),{recursive:true});
 for(const channel of ['stable','nightly']){
   const accent=channel==='stable'?'#FC7A0F':'#2F80ED';const logo=path.join(repo,'docs/branding',channel==='stable'?'plektra-open-color.svg':'plektra-nightly-open-color.svg');
   for(const format of ['landscape','portrait']){
     const portrait=format==='portrait',W=portrait?1080:1920,H=portrait?1920:1080,dir=path.join(root,'exports',channel,format);fs.mkdirSync(dir,{recursive:true});
     for(const card of catalog){
       let body=`<rect width="${W}" height="${H}" fill="${channel==='nightly'?'#CFDDE7':'#F4F1E9'}"/>`;
       body+=image(logo,portrait?72:76,portrait?58:48,58,58)+text(channel==='stable'?'Plektra':'Plektra Nightly',portrait?148:152,portrait?99:89,31,'#292B2C',600);
       let titleY=portrait?211:296,titleSize=portrait?78:66;
       card.title.forEach((s,i)=>body+=text(s,portrait?72:76,titleY+i*(portrait?88:78),titleSize,'#242729',700));
       let by=titleY+(card.title.length-1)*(portrait?88:78)+(portrait?63:69);
       card.body.forEach((s,i)=>body+=text(s,portrait?72:76,by+i*43,portrait?29:28,'#535960'));
       body+=`<rect x="${portrait?72:76}" y="${by+71}" width="64" height="5" fill="${accent}"/>`;
       const box=portrait?[72,by+108,936,H-(by+108)-175]:[794,148,1048,790];body+=await contents(card,box,portrait);
       body+=text('Open source software under GPLv3',portrait?72:76,H-(portrait?94:55),portrait?25:25,'#535960');
       const gx=portrait?72:1460,gy=H-(portrait?65:82);
       body+=image(path.join(__dirname,'github.svg'),gx,gy,30,30)+text('pkb-rocks/plektra',gx+44,gy+25,portrait?27:27,'#292B2C',500);
       const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${body}</svg>`;
       const p=path.join(dir,card.id+'.svg');fs.writeFileSync(p,svg);execFileSync('rsvg-convert',['-o',path.join(dir,card.id+'.png'),p]);
     }
     console.log(channel,format,catalog.length+' cards');
   }
 }
 fs.writeFileSync(path.join(root,'assets.json'),JSON.stringify([...assets].map(p=>path.relative(root,p)),null,2)+'\n');
 // Contact sheets make all cards reviewable together; the full-size files remain separate.
 for(const channel of ['stable','nightly']){
   const tiles=[];for(let i=0;i<catalog.length;i++){let p=path.join(root,'exports',channel,'landscape',catalog[i].id+'.png');tiles.push({input:await sharp(p).resize(640,360).toBuffer(),left:(i%3)*640,top:Math.floor(i/3)*360});}
   await sharp({create:{width:1920,height:Math.ceil(catalog.length/3)*360,channels:3,background:'#F4F1E9'}}).composite(tiles).png().toFile(path.join(root,'previews',channel+'-overview.png'));
 }
})().catch(e=>{console.error(e);process.exit(1)});
