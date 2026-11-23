# Genera pagar-reserva.svg (diagrama de secuencia de "Pagar reserva") sin dependencias.
from xml.sax.saxutils import escape

P = ["Turista\n(navegador)", "PagoWebController", "PagoService", "Base de datos", "PasarelaPago", "Mercado Pago\n(API)"]
X = [90, 290, 520, 750, 960, 1170]
W, TOP, STEP = 1300, 90, 34
steps = [
 ("msg",0,1,"GET /panel/turista/reservas/{id}/pagar",0),
 ("msg",1,2,"validarPago(turistaId, reserva)",0),
 ("msg",2,1,"null (se puede pagar) o motivo",1),
 ("msg",1,0,"pagar.html (formulario de Mercado Pago)",1),
 ("note","El navegador convierte la tarjeta en un token: el servidor nunca ve el número"),
 ("msg",0,1,"POST .../pagar (token, medio de pago)",0),
 ("msg",1,2,"pagar(turistaId, reservaId, datos)",0),
 ("msg",2,3,"findById(reserva)",0),
 ("msg",3,2,"reserva",1),
 ("frame","alt  no se puede pagar (ajena, cancelada, vencida, ya paga, en espera)"),
 ("msg",2,1,"RespuestaPago(null, motivo)",1),
 ("msg",1,0,"JSON { NO_PERMITIDO, mensaje }  (no se llama a la pasarela)",1),
 ("end",),
 ("self",2,"monto = reserva.montoTotal (precio x cupos)"),
 ("msg",2,4,"cobrar(solicitud)",0),
 ("msg",4,5,"POST /v1/payments (token, monto, timeout)",0),
 ("frame","alt  aprobado"),
 ("msg",5,4,"approved + id",1),
 ("msg",4,2,"ResultadoPago(APROBADO)",1),
 ("msg",2,3,"INSERT Pago (APROBADO, id externo, últimos 4)",0),
 ("msg",2,3,"UPDATE reserva: estado = CONFIRMADA",0),
 ("msg",2,1,"RespuestaPago(APROBADO)",1),
 ("msg",1,0,"JSON { aprobado, comprobante }",1),
 ("else","else  rechazado"),
 ("msg",5,4,"rejected + motivo",1),
 ("msg",4,2,"ResultadoPago(RECHAZADO)",1),
 ("msg",2,3,"INSERT Pago (RECHAZADO)",0),
 ("self",2,"la reserva sigue PENDIENTE_PAGO"),
 ("msg",2,1,"RespuestaPago(RECHAZADO)",1),
 ("msg",1,0,"JSON { mensaje }  (se puede reintentar)",1),
 ("else","else  en revisión"),
 ("msg",5,4,"in_process",1),
 ("msg",2,3,"INSERT Pago (PENDIENTE)",0),
 ("self",2,"la reserva sigue PENDIENTE_PAGO"),
 ("msg",2,1,"RespuestaPago(PENDIENTE)",1),
 ("msg",1,0,"JSON { en revisión }",1),
 ("else","else  pasarela caída, sin respuesta o timeout"),
 ("msg",4,2,"excepción",2),
 ("msg",2,3,"INSERT Pago (ERROR)",0),
 ("self",2,"la reserva sigue PENDIENTE_PAGO; no se rompe"),
 ("msg",2,1,"RespuestaPago(ERROR)",1),
 ("msg",1,0,"JSON { mensaje }  (se puede reintentar)",1),
 ("end",),
]
y = TOP + 70; ops = []; frames = []; stack = []
for s in steps:
    k = s[0]
    if k == "frame":
        y += 8; stack.append([s[1], y, []]); y += 24
    elif k == "else":
        stack[-1][2].append((y - 6, s[1])); y += 22
    elif k == "end":
        lab, y0, seps = stack.pop(); frames.append((lab, y0, y + 4, seps)); y += 14
    elif k == "msg":
        ops.append(("msg", y + 14, *s[1:])); y += STEP
    elif k == "note":
        ops.append(("note", y + 14, s[1])); y += STEP
    else:
        ops.append(("self", y + 14, *s[1:])); y += STEP
H = y + 30
o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="Arial, sans-serif" font-size="12.5">',
     '<defs><marker id="a" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto"><path d="M0,0 L10,4 L0,8 z" fill="#1b2a41"/></marker>'
     '<marker id="x" markerWidth="10" markerHeight="10" refX="5" refY="5" orient="auto"><path d="M1,1 L9,9 M9,1 L1,9" stroke="#b9483a" stroke-width="2"/></marker></defs>',
     f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
     f'<text x="{W/2}" y="28" text-anchor="middle" font-size="18" font-weight="bold" fill="#1b2a41">Diagrama de secuencia: Pagar reserva (CU-T07)</text>']
for lab, y0, y1, seps in frames:
    o.append(f'<rect x="40" y="{y0}" width="{W-80}" height="{y1-y0}" fill="#fdf3ec" fill-opacity="0.55" stroke="#e0713a" stroke-width="1.3"/>')
    o.append(f'<text x="48" y="{y0+16}" font-weight="bold" fill="#c95f2b">{escape(lab)}</text>')
    for ys, t in seps:
        o.append(f'<line x1="40" y1="{ys}" x2="{W-40}" y2="{ys}" stroke="#e0713a" stroke-dasharray="6 4"/>')
        o.append(f'<text x="48" y="{ys+16}" font-weight="bold" fill="#c95f2b">{escape(t)}</text>')
for n, x in zip(P, X):
    o.append(f'<line x1="{x}" y1="{TOP+40}" x2="{x}" y2="{H-14}" stroke="#9aa3ad" stroke-dasharray="5 4"/>')
    lines = n.split("\n")
    o.append(f'<rect x="{x-80}" y="{TOP-30}" width="160" height="{26+14*len(lines)}" rx="6" fill="#1b2a41"/>')
    for j, l in enumerate(lines):
        o.append(f'<text x="{x}" y="{TOP-10+14*j}" text-anchor="middle" fill="#fff" font-weight="bold">{escape(l)}</text>')
for op in ops:
    if op[0] == "msg":
        _, yy, a, b, t, d = op
        xa, xb = X[a], X[b]
        dash = ' stroke-dasharray="6 4"' if d else ''
        err = d == 2
        col = "#b9483a" if err else "#1b2a41"
        mk = "x" if err else "a"
        o.append(f'<line x1="{xa}" y1="{yy}" x2="{xb + (-2 if xb>xa else 2)}" y2="{yy}" stroke="{col}" stroke-width="1.4"{dash} marker-end="url(#{mk})"/>')
        o.append(f'<text x="{(xa+xb)/2}" y="{yy-5}" text-anchor="middle" fill="{col}">{escape(t)}</text>')
    elif op[0] == "note":
        _, yy, t = op
        w = 7 * len(t) * 0.95
        o.append(f'<rect x="{X[0]+6}" y="{yy-12}" width="{w:.0f}" height="22" fill="#fff6bf" stroke="#d9c75a"/>')
        o.append(f'<text x="{X[0]+12}" y="{yy+3}">{escape(t)}</text>')
    else:
        _, yy, p, t = op
        x = X[p]
        o.append(f'<rect x="{x+6}" y="{yy-12}" width="{7*len(t)*0.95:.0f}" height="22" fill="#fff6bf" stroke="#d9c75a"/>')
        o.append(f'<text x="{x+12}" y="{yy+3}">{escape(t)}</text>')
o.append('</svg>')
open("pagar-reserva.svg", "w", encoding="utf-8").write("\n".join(o))
print(W, H)
