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
}

export interface ChatSummary {
  id: string;
  otherUserId: string;
  otherUserName: string;
  otherUserPhotoKey: string | null;
  createdAt: string;
}

export interface DriverLinkCode {
  linkCode: string;
}

export interface ChatPage<T> {
  content: T[];
  number: number;
  totalPages: number;
  last: boolean;
}
