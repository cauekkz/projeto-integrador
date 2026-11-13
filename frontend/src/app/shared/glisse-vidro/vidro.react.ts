// ponte React -> Angular: monta o Glass original da Glisse (React) como camada de fundo de um elemento do Angular.
// so e carregado por import() no GlisseVidro, entao o React fica fora do pacote inicial
import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { Glass } from 'glisse';

export function montarVidro(alvo: HTMLElement) {
  const raiz = createRoot(alvo);
  raiz.render(createElement(Glass, { className: 'glisse-vidro-camada', 'aria-hidden': true }));
  return () => raiz.unmount();
}
