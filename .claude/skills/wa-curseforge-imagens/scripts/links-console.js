// Cole no console do navegador (F12) na página Images do projeto no CurseForge
// (authors.curseforge.com → projeto → Images). Lista todas as imagens enviadas no
// formato "arquivo.png -> https://media.forgecdn.net/attachments/<a>/<b>/arquivo-png.png"
// e copia o resultado para a área de transferência. As miniaturas ficam em
// /attachments/thumbnails/<a>/<b>/<w>/<h>/... e viram o link cheio.
(() => {
  const urls = new Set();
  for (const el of document.querySelectorAll('img[src*="media.forgecdn.net/attachments"], a[href*="media.forgecdn.net/attachments"]')) {
    const u = el.src || el.href;
    const m = u.match(/media\.forgecdn\.net\/attachments\/(?:thumbnails\/)?(\d+)\/(\d+)\/(?:\d+\/\d+\/)?([^/?#]+)/);
    if (m) urls.add(`https://media.forgecdn.net/attachments/${m[1]}/${m[2]}/${m[3]}`);
  }
  const linhas = [...urls].sort().map(u => {
    const nome = u.split('/').pop().replace(/-png\.png$/, '.png').replace(/-jpg\.jpg$/, '.jpg');
    return `${nome} -> ${u}`;
  });
  const texto = linhas.join('\n');
  console.log(texto || 'nenhuma imagem encontrada nesta página');
  if (texto && navigator.clipboard) navigator.clipboard.writeText(texto).then(() => console.log(`${linhas.length} links copiados`));
  return linhas.length;
})();
