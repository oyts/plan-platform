export const PASSWORD_MISMATCH = '两次输入的密码不一致，请重新确认。';
export function registrationPasswordError(password: string, confirmation: string): string {
  if (password.length < 10 || password.length > 72 || !/[A-Za-z]/.test(password) || !/\d/.test(password))
    return '密码需为 10—72 位，并同时包含英文字母和数字。';
  if (!confirmation) return '请再次输入密码。';
  return password === confirmation ? '' : PASSWORD_MISMATCH;
}
