import {defineConfig} from '@playwright/test';
import shared from './playwright.config';

// Every historical-closure.spec test installs its explicit API fixture before navigation.
// Real wire/transaction behavior is verified by the selected Spring integration tests.
// Do not start an unused Java service whose Windows teardown can block this mock-only suite.
const servers=Array.isArray(shared.webServer)?shared.webServer:[];
if(servers.length!==2)throw Error('Review the shared server configuration before reusing it.');
export default defineConfig({...shared,testMatch:'historical-closure.spec.ts',webServer:[servers[1]]});
