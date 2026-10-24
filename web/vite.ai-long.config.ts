import {mergeConfig,defineConfig} from 'vite';
import base from './vite.config';
export default mergeConfig(base,defineConfig({server:{proxy:{'/api':'http://127.0.0.1:43174','/actuator':'http://127.0.0.1:43174'}},preview:{proxy:{'/api':'http://127.0.0.1:43174','/actuator':'http://127.0.0.1:43174'}}}));
