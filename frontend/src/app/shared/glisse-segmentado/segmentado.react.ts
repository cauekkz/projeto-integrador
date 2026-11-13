// ponte React -> Angular: monta o SegmentedControl original da Glisse (React) dentro de um elemento do Angular.
// so e carregado por import() no GlisseSegmentado, entao o React fica num pedaco separado do app
import { createElement } from 'react';
import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import { SegmentedControl } from 'glisse';

export interface OpcaoSegmentada {
  value: string;
  label: string;
}

export interface PropsSegmentado {
  opcoes: OpcaoSegmentada[];
  valor: string | null;
  ariaLabel: string;
  aoEscolher: (valor: string) => void;
}

export function montarSegmentado(alvo: HTMLElement) {
  const raiz = createRoot(alvo);

  const desenhar = (props: PropsSegmentado, valor: string | null) => {
    raiz.render(
      createElement(SegmentedControl, {
        'aria-label': props.ariaLabel,
        value: valor,
        onValueChange: (v: string) => {
          // o polegar fica no segmento tocado ja no toque (sem voltar e ir de novo)
          if (v !== valor) flushSync(() => desenhar(props, v));
          props.aoEscolher(v);
        },
        items: props.opcoes.map((o) => ({ value: o.value, label: o.label })),
      }),
    );
  };

  return {
    render(props: PropsSegmentado) {
      desenhar(props, props.valor);
    },
    desmontar() {
      raiz.unmount();
    },
  };
}

export type SegmentadoMontado = ReturnType<typeof montarSegmentado>;
