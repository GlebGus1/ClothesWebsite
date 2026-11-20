import {postJson, getJson} from './http';
import type {AuthResponse, UserDto} from '../types/api';

export interface Credentials {
  email: string;
  password: string;
}

export interface RegisterPayload extends Credentials {
  firstName: string;
  lastName: string;
}

export const login = (payload: Credentials): Promise<AuthResponse> => postJson<AuthResponse>('/auth/login', payload);

export const register = (payload: RegisterPayload): Promise<AuthResponse> =>
  postJson<AuthResponse>('/auth/register', payload);

/** Вызывать только при активном access-токене (или после удачного refresh). */
export const me = (): Promise<UserDto> => getJson<UserDto>('/auth/me');
