export interface ChatMessage {
  id: string;
  senderId: string;
  content: string;
  attachmentUrl: string | null;
  attachmentType: string | null;
  sentAt: string;
  // REGULAR_CHAT = mensagem normal; PROPOSAL, APPROVAL, REJECTION e ABSENCE sao do sistema
  messageType: string;
  payload: string | null;
  // quando o outro leu: null = ✓ (enviada), preenchida = ✓✓ (lida)
  readAt: string | null;
}

// aviso de leitura do WebSocket do chat: quem leu (userId) marcou as mensagens do outro como lidas
export interface ChatReadEvent {
  type: 'read';
  chatId: string;
  userId: string;
  readAt: string;
}

export interface ChatSummary {
  id: string;
  otherUserId: string;
  otherUserName: string;
  otherUserPhotoKey: string | null;
  createdAt: string;
  // mensagens do outro que eu ainda nao li
  unreadCount: number;
}

export interface DriverLinkCode {
  linkCode: string;
}

// as paginas usam o Page<T> generico de services/dto/page.dto.ts
