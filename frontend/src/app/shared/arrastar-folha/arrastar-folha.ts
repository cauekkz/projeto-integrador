// puxar a folha de baixo pra fechar (como no iPhone), pra todas as folhas do app de uma vez.
// um ouvinte so no documento: comeca o arrasto em qualquer .folha de um .fundo (menos os pop-ups de confirmacao),
// a folha segue o dedo 1:1, pra cima ela resiste (atrito), e ao soltar fecha se passou de 1/4 da altura
// ou se foi um puxao rapido. fechar = clicar no .fundo, que e o que cada tela ja usa pra fechar.

const LIMITE_VELOCIDADE = 0.11; // px/ms: um puxao rapido ja fecha
const ALVOS_DE_TOQUE = 'button, a, input, select, textarea, label, [contenteditable], [role="slider"]';

export function ligarArrastarFolha(doc: Document = document): () => void {
  let folha: HTMLElement | null = null;
  let fundo: HTMLElement | null = null;
  let id = -1;
  let inicioY = 0;
  let inicioT = 0;
  let deslocamento = 0;
  let arrastando = false;

  const aoApertar = (e: PointerEvent) => {
    if (folha || e.button !== 0) return;
    const alvo = e.target as HTMLElement | null;
    const f = alvo?.closest<HTMLElement>('.fundo:not(.confirmacao) > .folha');
    if (!f || alvo!.closest(ALVOS_DE_TOQUE)) return;
    // folha com rolagem: so puxa quando ja esta no topo
    if (f.scrollTop > 0) return;
    folha = f;
    fundo = f.parentElement;
    id = e.pointerId;
    inicioY = e.clientY;
    inicioT = performance.now();
    deslocamento = 0;
    arrastando = false;
  };

  const aoMover = (e: PointerEvent) => {
    if (!folha || e.pointerId !== id) return;
    const dy = e.clientY - inicioY;
    if (!arrastando) {
      if (Math.abs(dy) < 6) return;
      // comecou pra cima: e rolagem do conteudo, nao arrasto
      if (dy < 0) {
        folha = fundo = null;
        return;
      }
      arrastando = true;
      folha.setPointerCapture?.(id);
      folha.style.transition = 'none';
      folha.style.animation = 'none';
    }
    // pra baixo segue o dedo; pra cima resiste cada vez mais
    deslocamento = dy >= 0 ? dy : -Math.pow(-dy, 0.6);
    folha.style.transform = `translateY(${deslocamento}px)`;
  };

  const aoSoltar = (e: PointerEvent) => {
    if (!folha || e.pointerId !== id) return;
    const f = folha;
    const fu = fundo;
    folha = fundo = null;
    if (!arrastando) return;
    const velocidade = deslocamento / Math.max(1, performance.now() - inicioT);
    const fecha = deslocamento > f.offsetHeight * 0.25 || (deslocamento > 20 && velocidade > LIMITE_VELOCIDADE);
    if (fecha && fu) {
      // a animacao de saida (fundo-sair) desce a folha a partir de onde ela esta
      fu.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    }
    // o clique de verdade que vem logo depois de soltar nao pode chegar no que estava embaixo do dedo
    const engolir = (c: Event) => c.stopPropagation();
    doc.addEventListener('click', engolir, { capture: true, once: true });
    setTimeout(() => doc.removeEventListener('click', engolir, { capture: true }), 0);
    if (fecha) return;
    // nao fechou: volta pro lugar
    f.style.transition = 'transform 300ms cubic-bezier(0.32, 0.72, 0, 1)';
    f.style.transform = '';
    f.addEventListener('transitionend', () => (f.style.transition = ''), { once: true });
  };

  // no toque o navegador rola a pagina e cancela o ponteiro: enquanto arrasta a folha, segura a rolagem
  const aoTocarMover = (e: TouchEvent) => {
    if (folha && arrastando && e.cancelable) e.preventDefault();
  };

  doc.addEventListener('pointerdown', aoApertar);
  doc.addEventListener('touchmove', aoTocarMover, { passive: false });
  doc.addEventListener('pointermove', aoMover);
  doc.addEventListener('pointerup', aoSoltar);
  doc.addEventListener('pointercancel', aoSoltar);
  return () => {
    doc.removeEventListener('pointerdown', aoApertar);
    doc.removeEventListener('touchmove', aoTocarMover);
    doc.removeEventListener('pointermove', aoMover);
    doc.removeEventListener('pointerup', aoSoltar);
    doc.removeEventListener('pointercancel', aoSoltar);
  };
}
