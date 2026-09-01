from __future__ import annotations

import os

import streamlit as st
from dotenv import load_dotenv

from kutalp.brain import answer, api_configured, red_team
from kutalp.db import add_decision, add_memory, delete_memory, init_db, list_memories, relevant_memories

load_dotenv()
init_db()

st.set_page_config(page_title="KUTALP PRIME", page_icon="🜂", layout="wide")

st.markdown(
    """
<style>
.block-container {max-width: 1100px; padding-top: 1.4rem;}
.k-title {font-size: 2.25rem; font-weight: 800; letter-spacing: .06em;}
.k-sub {opacity:.72; margin-top:-.5rem; margin-bottom:1.2rem;}
.status {padding:.65rem .85rem; border:1px solid rgba(128,128,128,.25); border-radius:12px;}
</style>
""",
    unsafe_allow_html=True,
)

st.markdown('<div class="k-title">KUTALP PRIME</div>', unsafe_allow_html=True)
st.markdown('<div class="k-sub">Kişisel Yapay Zekâ Başdanışman · V0.3 · Metin Konsolu</div>', unsafe_allow_html=True)

with st.sidebar:
    st.subheader("Çekirdek")
    model = os.getenv("KUTALP_MODEL", "gpt-5.6-terra")
    st.write(f"Model: `{model}`")
    if api_configured():
        st.success("AI bağlantısı aktif")
    else:
        st.warning("Offline çekirdek")
    scientific = st.toggle("Bilimsel mod", value=True)
    use_red_team = st.toggle("Red Team", value=True)
    st.caption("Red Team açıkken önemli cevaplar bağımsız ikinci eleştiriden geçer.")

chat_tab, memory_tab, system_tab = st.tabs(["🧠 Komuta", "🗃️ Hafıza", "⚙️ Sistem"])

with chat_tab:
    if "messages" not in st.session_state:
        st.session_state.messages = []

    for msg in st.session_state.messages:
        with st.chat_message(msg["role"]):
            st.markdown(msg["content"])
            if msg.get("red_team"):
                with st.expander("🛡️ Red Team eleştirisi"):
                    st.markdown(msg["red_team"])

    prompt = st.chat_input("KUTALP'a sor...")
    if prompt:
        st.session_state.messages.append({"role": "user", "content": prompt})
        with st.chat_message("user"):
            st.markdown(prompt)

        memories = relevant_memories(prompt)
        with st.chat_message("assistant"):
            with st.spinner("Analiz ediliyor..."):
                try:
                    reply = answer(prompt, memories, scientific=scientific)
                    critique = red_team(prompt, reply, memories) if use_red_team and api_configured() else None
                except Exception as exc:
                    reply = f"Çekirdek hatası: `{type(exc).__name__}` — {exc}"
                    critique = None
            st.markdown(reply)
            if critique:
                with st.expander("🛡️ Red Team eleştirisi", expanded=True):
                    st.markdown(critique)
            add_decision(prompt, reply, critique)
            st.session_state.messages.append(
                {"role": "assistant", "content": reply, "red_team": critique}
            )

        c1, c2 = st.columns([1, 3])
        with c1:
            if st.button("Bu isteği hafızaya kaydet", use_container_width=True):
                add_memory(prompt, "user_note")
                st.toast("Kalıcı hafızaya kaydedildi")

with memory_tab:
    st.subheader("Kullanıcı kontrollü uzun dönem hafıza")
    st.caption("KUTALP V0.3 bilgiyi kendi kafasına göre kalıcılaştırmaz. Sen eklersin, sen silersin.")

    with st.form("memory_form", clear_on_submit=True):
        memory_text = st.text_area("Hatırlanacak bilgi")
        memory_kind = st.selectbox("Tür", ["general", "goal", "preference", "project", "rule", "user_note"])
        submitted = st.form_submit_button("Hafızaya ekle")
        if submitted and memory_text.strip():
            add_memory(memory_text, memory_kind)
            st.success("Kaydedildi")

    memories = list_memories()
    if not memories:
        st.info("Henüz kalıcı hafıza yok.")
    for m in memories:
        c1, c2 = st.columns([9, 1])
        with c1:
            st.markdown(f"**{m.kind}** · {m.text}")
            st.caption(m.created_at)
        with c2:
            if st.button("Sil", key=f"del-{m.id}"):
                delete_memory(m.id)
                st.rerun()

with system_tab:
    st.subheader("V0.3 durum")
    st.markdown(
        """
- ✅ Yerel SQLite uzun dönem hafıza
- ✅ Bilimsel düşünme modu
- ✅ Bağımsız Red Team eleştirisi
- ✅ Karar geçmişi kaydı
- ✅ Telefon uyumlu web arayüzü
- ✅ OpenAI Responses API bağlantısı
- ✅ V0.3 PWA: gerçek zamanlı WebRTC ses
- ✅ İzinli yerel araçlar + görevler + tahmin defteri
- ✅ Araç audit log
- ⏭️ V0.4: görüntü, gözlük ve kamera algısı
- ⏭️ V0.5: yerel GPU modeli + vektör hafıza
- ⏭️ V0.6: Gmail / Takvim / GitHub bağlayıcıları
- ⏭️ V1.0: çoklu uzman ajan orkestrasyonu
"""
    )
