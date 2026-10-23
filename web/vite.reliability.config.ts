import {defineConfig} from 'vite';
import base from './vite.config';
export default defineConfig({...base,preview:{proxy:{'/api':'http://127.0.0.1:43171','/actuator':'http://127.0.0.1:43171'}}});
