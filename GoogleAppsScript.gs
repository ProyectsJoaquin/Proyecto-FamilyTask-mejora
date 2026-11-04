// HogarSmart - Google Apps Script backend
// 1) Create a Google Calendar named TAREAS DOMÉSTICAS.
// 2) Paste its Calendar ID and a long random token below.
// 3) Deploy as Web app: Execute as Me. Restrict access as much as your Google account allows.
const CALENDAR_ID = 'PEGA_AQUI_EL_ID_DEL_CALENDARIO';
const ACCESS_TOKEN = 'REEMPLAZA_POR_UN_TOKEN_LARGO_Y_ALEATORIO';

function output(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
function authorized(token) { return token && token === ACCESS_TOKEN; }

function doGet(e) {
  if (!authorized(e.parameter.token)) return output({ok:false,error:'No autorizado'});
  const user = String(e.parameter.user || '').trim().toLowerCase();
  if (!user) return output({ok:false,error:'Falta el nombre del integrante'});
  const calendar = CalendarApp.getCalendarById(CALENDAR_ID);
  if (!calendar) return output({ok:false,error:'Calendario no encontrado'});
  const now = new Date();
  const until = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
  const events = calendar.getEvents(now, until).filter(event => {
    const description = (event.getDescription() || '').toLowerCase();
    return description.includes('responsable: ' + user) && /estado:\s*pendiente/i.test(description);
  }).sort((a,b) => a.getStartTime() - b.getStartTime());
  if (!events.length) return output({ok:true,found:false,message:'No hay tareas pendientes'});
  const event = events[0];
  return output({ok:true,found:true,id:event.getId(),nombre:event.getTitle(),inicio:event.getStartTime().toISOString(),fin:event.getEndTime().toISOString(),responsable:user});
}

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents || '{}');
    if (!authorized(data.token)) return output({ok:false,error:'No autorizado'});
    if (data.action !== 'complete' || !data.id) return output({ok:false,error:'Acción o ID inválido'});
    const calendar = CalendarApp.getCalendarById(CALENDAR_ID);
    const event = calendar && calendar.getEventById(data.id);
    if (!event) return output({ok:false,error:'Evento no encontrado'});
    let description = event.getDescription() || '';
    if (/estado:\s*pendiente/i.test(description)) description = description.replace(/estado:\s*pendiente/i,'ESTADO: CUMPLIDA');
    else if (!/estado:\s*cumplida/i.test(description)) description += '\nESTADO: CUMPLIDA';
    event.setDescription(description);
    return output({ok:true,message:'Tarea completada'});
  } catch (err) { return output({ok:false,error:String(err)}); }
}
