import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import localizedFormat from 'dayjs/plugin/localizedFormat';
import utc from 'dayjs/plugin/utc';

/**
 * The single configured dayjs instance for the app. Import THIS, not `dayjs` directly, so plugins
 * are guaranteed loaded and date handling is consistent everywhere.
 */
dayjs.extend(relativeTime);
dayjs.extend(localizedFormat);
dayjs.extend(utc);

export default dayjs;
