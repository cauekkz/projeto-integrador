import { Injectable } from '@angular/core'

@Injectable({
  providedIn: 'root'
})
export class RegisterService {
  selectedFile: File | null = null;
}