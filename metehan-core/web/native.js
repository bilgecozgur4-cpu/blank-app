(() => {
  const originalPrepareToolCall = prepareToolCall;

  prepareToolCall = async function(call) {
    let args = {};
    try {
      args = typeof call.arguments === 'string' ? JSON.parse(call.arguments || '{}') : (call.arguments || {});
    } catch (e) {
      return sendToolOutput(call.call_id, {ok:false, error:`Geçersiz araç argümanı: ${e.message}`});
    }

    const meta = toolMeta.get(call.name);
    if (meta?.client_side) {
      addLiveLine(`Telefon eylemi önerildi: ${call.name}`, 'tool');
      if (window.AndroidMetehan?.proposeAction) {
        window.AndroidMetehan.proposeAction(call.call_id, JSON.stringify(args));
      } else {
        sendToolOutput(call.call_id, {ok:false, error:'Native Android köprüsü bu oturumda kullanılamıyor.'});
      }
      return;
    }
    return originalPrepareToolCall(call);
  };

  window.metehanNativeActionResult = function(callId, resultJson) {
    let result;
    try { result = JSON.parse(resultJson); }
    catch (_) { result = {ok:false, error:'Native sonuç çözümlenemedi'}; }
    sendToolOutput(callId, result);
  };
})();
