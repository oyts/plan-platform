import axios from 'axios';
export interface ServerConfig {
  registrationEnabled: boolean; passwordResetEnabled: boolean; invitationEnabled: boolean;
  emailVerificationEnabled: boolean; planRestDaysEnabled?: boolean; todoEnabled?: boolean;
}
export function validateServerConfig(value: unknown): ServerConfig {
  if (!value || typeof value !== 'object' || typeof (value as ServerConfig).registrationEnabled !== 'boolean')
    throw new Error('此地址没有提供新版账号接口，可能仍运行旧版计划控制台；请先部署 Spring Boot 多用户版本。');
  return value as ServerConfig;
}
export function serverConnectionIssue(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 404) return '服务器可以访问，但新版账号接口不存在（HTTP 404）。请先部署 Spring Boot 多用户版本，并核对 /api 转发。';
    if (error.response) return `账号接口返回 HTTP ${error.response.status}，请检查服务器配置与访问权限。`;
    return '无法连接服务器。请检查后端是否启动、服务器地址、网络和 HTTPS 证书。';
  }
  return error instanceof Error ? error.message : '账号服务暂不可用，请稍后重试。';
}
