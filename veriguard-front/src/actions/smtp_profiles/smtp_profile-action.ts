import {
  simpleCall,
  simpleDelCall,
  simplePostCall,
  simplePutCall,
} from '../../utils/Action';
import type { SmtpProfile, SmtpProfileInput } from '../../utils/api-types.d';

const SMTP_PROFILE_URI = '/api/smtp_profiles';

export const fetchSmtpProfiles = (): Promise<{ data: SmtpProfile[] }> =>
  simpleCall(SMTP_PROFILE_URI) as Promise<{ data: SmtpProfile[] }>;

export const createSmtpProfile = (
  input: SmtpProfileInput,
): Promise<{ data: SmtpProfile }> =>
  simplePostCall(SMTP_PROFILE_URI, input) as Promise<{ data: SmtpProfile }>;

export const updateSmtpProfile = (
  id: string,
  input: SmtpProfileInput,
): Promise<{ data: SmtpProfile }> =>
  simplePutCall(`${SMTP_PROFILE_URI}/${id}`, input) as Promise<{ data: SmtpProfile }>;

export const deleteSmtpProfile = (id: string): Promise<unknown> =>
  simpleDelCall(`${SMTP_PROFILE_URI}/${id}`) as Promise<unknown>;
