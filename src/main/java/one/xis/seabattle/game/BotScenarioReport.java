package one.xis.seabattle.game;

import com.google.gson.Gson;

final class BotScenarioReport {
    private BotScenarioReport() {}

    static String html(BotTestRun run) {
        var setup = ScenarioScriptParser.parse(run.script());
        String json = new Gson().toJson(new Report(run, setup.worldMap().landmasses(), ScenarioScriptParser.grid(run.script())));
        // Keep user-controlled names and scripts inside JSON, never HTML markup.
        json = json.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        return """
                <!doctype html><html lang="de"><meta charset="utf-8">
                <title>Bot-Szenario</title>
                <style>
                body{background:#10242d;color:#e2edef;font:16px system-ui;margin:24px;max-width:1250px}
                canvas{width:min(90vw,850px);height:auto;background:#071d27;border:1px solid #64818a}
                .controls{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
                button,input{margin:8px} table{border-collapse:collapse} td,th{padding:6px;text-align:left}
                pre{white-space:pre;overflow:auto} small{color:#b7cbd0}
                </style>
                <h1>Bot-Szenario</h1>
                <p id="timing"></p>
                <p>Helle und dunkle Schiffe sind mit ihrer Rasterkennung beschriftet. Pfeile zeigen den Bug.
                Die Linien zeigen Fahrwege; Kreuze markieren zerstörte Schiffe. Das Raster entspricht der Eingabedatei (0° nach unten).</p>
                <canvas id="map" width="1000" height="1000"></canvas>
                <div class="controls"><button id="play">Abspielen</button><button id="reset">Anfang</button>
                <input id="seek" aria-label="Zeitpunkt" type="range" min="0" value="0" style="width:45%">
                <label><input type="checkbox" id="paths" checked>Fahrwege</label><b id="clock"></b></div>
                <table><thead><tr><th>Schiff</th><th>Typ / Tiefe</th><th>Geschwindigkeit</th><th>Gas / Ruder</th><th>Zustand / Leben</th></tr></thead><tbody id="ships"></tbody></table>
                <p><small>Geschwindigkeiten wie in der Simulation, Gasstellung 0–8 (2 = Stopp).
                Zeitmessung ohne Bericht und Momentaufnahmen. Einzelmessungen sind kein belastbarer Leistungsvergleich.
                Die Darstellung zeigt Rumpfmittelpunkte und schematische Rümpfe, keine exakten Kollisionsflächen.</small></p>
                <details><summary>Ausgangslage bearbeiten: scenario.txt</summary><pre id="source"></pre></details>
                <p>Die Datei trace.csv enthält sämtliche Positionen und Steuerbefehle im Abstand von 0,1 Sekunden.</p>
                <script id="data" type="application/json">__DATA__</script>
                <script>
                (() => {
                const data=JSON.parse(document.getElementById('data').textContent), frames=data.run.frames;
                const canvas=document.getElementById('map'),ctx=canvas.getContext('2d'),seek=document.getElementById('seek');
                const seekMax=frames.length-1;seek.max=seekMax;
                document.getElementById('source').textContent=data.run.script;
                const tm=data.run.timing;
                document.getElementById('timing').textContent=`Mittlere Bot-Runde: ${tm.bots.meanMs.toFixed(3)} ms · Gesamtrunde: ${tm.completeRound.meanMs.toFixed(3)} ms · p99: ${tm.completeRound.p99Ms.toFixed(3)} ms`;
                let loX=Infinity,loZ=Infinity,hiX=-Infinity,hiZ=-Infinity;
                function bound(x,z){loX=Math.min(loX,x);loZ=Math.min(loZ,z);hiX=Math.max(hiX,x);hiZ=Math.max(hiZ,z)}
                const grid=data.grid,cell=grid.cellSize,left=-grid.columns*cell/2,top=-grid.rows*cell/2;
                bound(left,top);bound(-left,-top);
                for(const f of frames)for(const s of f.ships)bound(s.x,s.z);
                for(const l of data.land){bound(l.x-l.rx,l.z-l.rz);bound(l.x+l.rx,l.z+l.rz)}
                const span=Math.max(100,hiX-loX,hiZ-loZ)+100,cx=(loX+hiX)/2,cz=(loZ+hiZ)/2,k=900/span;
                const px=x=>500+(x-cx)*k,pz=z=>500+(z-cz)*k;
                let playing=false,previous=0;
                function draw(){
                  const i=Number(seek.value),f=frames[i];ctx.clearRect(0,0,1000,1000);
                  ctx.strokeStyle='#26434f';ctx.lineWidth=1;
                  for(let g=0;g<=grid.columns;g++){ctx.beginPath();ctx.moveTo(px(left+g*cell),pz(top));ctx.lineTo(px(left+g*cell),pz(-top));ctx.stroke()}
                  for(let g=0;g<=grid.rows;g++){ctx.beginPath();ctx.moveTo(px(left),pz(top+g*cell));ctx.lineTo(px(-left),pz(top+g*cell));ctx.stroke()}
                  ctx.fillStyle='#58795b';for(const l of data.land){ctx.beginPath();ctx.ellipse(px(l.x),pz(l.z),l.rx*k,l.rz*k,0,0,Math.PI*2);ctx.fill()}
                  if(document.getElementById('paths').checked){
                    const tracks=new Map();for(let n=0;n<=i;n++)for(const s of frames[n].ships){
                      const key=s.id+':'+s.lifeId;if(!tracks.has(key))tracks.set(key,[]);tracks.get(key).push(s)}
                    for(const track of tracks.values()){ctx.strokeStyle=track[0].teamId==='light'?'#92dce7':'#ee9878';ctx.lineWidth=2;ctx.beginPath();track.forEach((s,j)=>j?ctx.lineTo(px(s.x),pz(s.z)):ctx.moveTo(px(s.x),pz(s.z)));ctx.stroke()}
                  }
                  for(const s of f.ships){ctx.save();ctx.translate(px(s.x),pz(s.z));ctx.rotate(-s.heading);
                    ctx.fillStyle=s.teamId==='light'?'#c6f3ff':'#f69473';ctx.beginPath();ctx.moveTo(0,11);ctx.lineTo(-5,-9);ctx.lineTo(5,-9);ctx.closePath();ctx.fill();
                    if(s.state!=='active'){ctx.strokeStyle='#ff4e54';ctx.beginPath();ctx.moveTo(-9,-9);ctx.lineTo(9,9);ctx.moveTo(9,-9);ctx.lineTo(-9,9);ctx.stroke()}
                    ctx.restore();ctx.font='bold 18px system-ui';ctx.fillStyle='white';ctx.fillText(s.id.slice(-1),px(s.x)+10,pz(s.z)-10)}
                  document.getElementById('clock').textContent=f.seconds.toFixed(1)+' s · Torpedos '+f.torpedoes+' · Bomben '+f.bombs+' · Wracks '+f.wrecks;
                  const tbody=document.getElementById('ships');tbody.replaceChildren();for(const s of f.ships){const tr=document.createElement('tr');
                    for(const value of [s.id,s.vehicleType+' / '+s.depthState,s.speed.toFixed(1),s.engineOrder+' / '+s.rudderDegrees+'°',s.state+' / '+s.lifeId]){const td=document.createElement('td');td.textContent=value;tr.append(td)}tbody.append(tr)}
                }
                seek.oninput=draw;document.getElementById('paths').onchange=draw;
                document.getElementById('reset').onclick=()=>{seek.value=0;draw()};
                document.getElementById('play').onclick=()=>{playing=!playing;document.getElementById('play').textContent=playing?'Pause':'Abspielen'};
                function animate(t){if(playing&&t-previous>=100){seek.value=Number(seek.value)>=seekMax?0:Number(seek.value)+1;previous=t;draw()}requestAnimationFrame(animate)}
                draw();requestAnimationFrame(animate);
                })();
                </script></html>
                """.replace("__DATA__", json);
    }

    private record Report(BotTestRun run, java.util.List<Landmass> land, ScenarioGrid grid) {}
}
