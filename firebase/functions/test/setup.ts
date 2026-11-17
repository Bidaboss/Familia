// Loaded before every test file (package.json, --require). Notices sent without a time take
// 14 h in Paris today: the suite gives the same result at any hour, « Calme la nuit » included.
// Tests about the night pass their own time to deliver.
import { setClockForTests } from '../src/prefs';

setClockForTests(() => new Date(`${new Date().toISOString().slice(0, 10)}T13:00:00Z`));
