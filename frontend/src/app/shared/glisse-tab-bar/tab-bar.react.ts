// ponte React -> Angular: monta a TabBar original da Glisse (React) dentro de um elemento do Angular.
// so e carregado por import() no GlisseTabBar, entao o React fica num pedaco separado do app
import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { TabBar } from 'glisse';

export interface AbaGlisse {
  value: string;
  /** nome da aba: fica escondido na tela, mas o leitor de tela le */
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
  return {
    render(props: PropsTabBar) {
      raiz.render(
        createElement(TabBar, {
          'aria-label': props.ariaLabel,
          value: props.valor,
          onValueChange: props.aoEscolher,
          items: props.abas.map((aba) => ({
            value: aba.value,
            label: createElement('span', { className: 'glisse-escondido' }, aba.label),
            icon: createElement('span', { className: aba.iconClass }),
          })),
        }),
      );
    },
    desmontar() {
      raiz.unmount();
    },
  };
}

export type TabBarMontada = ReturnType<typeof montarTabBar>;
