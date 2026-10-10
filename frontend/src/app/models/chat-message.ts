export interface ChatMessage {
  id: string;
  senderId: string;
  content: string;
  attachmentUrl: string | null;
  attachmentType: string | null;
  sentAt: string;
  payload: string | null;
}

export interface ChatSummary {
  id: string;
  otherUserId: string;
  otherUserName: string;
  createdAt: string;
}

export interface ChatPage<T> {
  content: T[];
  number: number;
  totalPages: number;
  last: boolean;
}
