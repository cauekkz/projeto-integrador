// ponte React -> Angular: monta a TabBar original da Glisse (React) dentro de um elemento do Angular.
// so e carregado por import() no GlisseTabBar, entao o React fica num pedaco separado do app
import { createElement } from 'react';
import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import { TabBar } from 'glisse';

export interface AbaGlisse {
  value: string;
  /** nome da aba: aparece embaixo do icone */
  label: string;
  /** classe do icone (bootstrap icons ou outra classe do projeto) */
  iconClass: string;
}

export interface PropsTabBar {
  abas: AbaGlisse[];
  valor: string | null;
  ariaLabel: string;
  aoEscolher: (valor: string) => void;
}

export function montarTabBar(alvo: HTMLElement) {
  const raiz = createRoot(alvo);
  let ultimas: PropsTabBar | null = null;

  const desenhar = (props: PropsTabBar, valor: string | null) => {
    raiz.render(
      createElement(TabBar, {
        'aria-label': props.ariaLabel,
        value: valor,
        onValueChange: (v: string) => {
          // a bolha fica na aba tocada ja no toque: sem isso a Glisse volta pra aba da tela atual
          // (a navegacao ainda nao terminou) e depois vai de novo quando a tela nova abre
          if (v !== valor) flushSync(() => desenhar(props, v));
          props.aoEscolher(v);
        },
        items: props.abas.map((aba) => ({
          value: aba.value,
          label: aba.label,
          icon: createElement('span', { className: aba.iconClass }),
        })),
      }),
    );
  };

  return {
    render(props: PropsTabBar) {
      ultimas = props;
      desenhar(props, props.valor);
    },
    // a navegacao do toque nao aconteceu: a bolha volta pra aba da tela que continua aberta
    voltar() {
      if (ultimas) desenhar(ultimas, ultimas.valor);
    },
    desmontar() {
      raiz.unmount();
    },
  };
}

export type TabBarMontada = ReturnType<typeof montarTabBar>;
