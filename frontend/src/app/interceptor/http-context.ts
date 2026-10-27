import { HttpContextToken } from '@angular/common/http';

// requisicoes que tratam o erro na propria tela marcam isso pra nao aparecer popup duplicado
export const SKIP_ERROR_POPUP = new HttpContextToken<boolean>(() => false);
