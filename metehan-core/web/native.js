(() => {
  const originalPrepareToolCall = window.prepareToolCall;

  window.prepareToolCall = async function(call) {
    let args = {};
    try {
      args = typeof call.arguments === 'string' ? JSON.parse(call.arguments || '{}') : (call.arguments || {});
    } catch (e) {
      return window.sendToolOutput(call.call_id, {ok:false, error:`Geçersiz araç argümanı: ${e.message}`});
    }

    const meta = window.toolMeta?.get ? window.toolMeta.get(call.name) : null;
    if (meta?.client_side) {
      window.addLiveLine?.(`Telefon eylemi önerildi: ${call.name}`, 'tool');
      if (window.AndroidMetehan?.proposeAction) {
        window.AndroidMetehan.proposeAction(call.call_id, JSON.stringify(args));
      } else {
        window.sendToolOutput(call.call_id, {ok:false, error:'Native Android köprüsü bu oturumda kullanılamıyor.'});
      }
      return;
    }
    return originalPrepareToolCall(call);
  };

  window.metehanNativeActionResult = function(callId, resultJson) {
    let result;
    try { result = JSON.parse(resultJson); }
    catch (_) { result = {ok:false, error:'Native sonuç çözümlenemedi'}; }
    window.sendToolOutput(callId, result);
  };
})();
