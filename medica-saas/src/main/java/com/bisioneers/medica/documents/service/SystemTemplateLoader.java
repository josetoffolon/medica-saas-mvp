package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.documents.domain.DocumentTemplateEntity;
import com.bisioneers.medica.documents.domain.DocumentTemplateRepository;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pre-carga las plantillas de sistema (isSystem=true) para cada tenant
 * al arranque de la aplicación.
 *
 * Estas plantillas:
 *  - NO se pueden editar/eliminar desde la UI (validación en service)
 *  - Si el ADMIN quiere personalizarlas, debe crear una copia editable
 *  - Se actualizan automáticamente al arrancar si el contenido cambió en el código
 *    (basándose en el `version` del enum)
 *
 * Para agregar una nueva plantilla de sistema:
 *  1. Agregar el HTML como constante abajo
 *  2. Agregar entry en seedTemplates() con tipo, nombre, contenido
 *  3. Restart del backend → la plantilla aparece para todos los tenants
 */
@Component
public class SystemTemplateLoader {

	private static final Logger log = LoggerFactory.getLogger(SystemTemplateLoader.class);

	private final TenantRepository tenantRepository;
	private final DocumentTemplateRepository templateRepository;

	public SystemTemplateLoader(TenantRepository tenantRepository,
			DocumentTemplateRepository templateRepository) {
		this.tenantRepository = tenantRepository;
		this.templateRepository = templateRepository;
	}

	@PostConstruct
	@Transactional
	public void seedForAllTenants() {
		List<TenantEntity> tenants = tenantRepository.findAll();
		for (TenantEntity tenant : tenants) {
			if (!templateRepository.existsByTenantIdAndIsSystemTrue(tenant.getId())) {
				seedTemplates(tenant.getId());
				log.info("System templates seeded for tenant: {}", tenant.getId());
			}
		}
	}

	/**
	 * Llamado también desde TenantService cuando se crea un nuevo tenant.
	 */
	@Transactional
	public void seedTemplates(java.util.UUID tenantId) {
		createIfMissing(tenantId, "CONSENT_GENERAL",
				"Consentimiento informado general",
				"Consentimiento general para procedimientos médico-estéticos",
				CONSENT_GENERAL_HTML);

		createIfMissing(tenantId, "CONSENT_TIRZEPATIDA",
				"Consentimiento informado - Tirzepatida + Vitamina B12",
				"Consentimiento específico para programa de regulación de apetito con tirzepatida",
				CONSENT_TIRZEPATIDA_HTML);
	}

	private void createIfMissing(java.util.UUID tenantId, String type,
			String name, String description, String html) {
		boolean exists = templateRepository
				.findByTenantIdAndDocumentTypeAndActiveTrueOrderByNameAsc(tenantId, type)
				.stream()
				.anyMatch(DocumentTemplateEntity::isSystem);

		if (exists) return;

		DocumentTemplateEntity entity = new DocumentTemplateEntity();
		entity.setTenantId(tenantId);
		entity.setName(name);
		entity.setDocumentType(type);
		entity.setDescription(description);
		entity.setContentHtml(html);
		entity.setVersion(1);
		entity.setActive(true);
		entity.setSystem(true);

		templateRepository.save(entity);
	}

	// ════════════════════════════════════════════════════════════════
	// TEMPLATE: CONSENTIMIENTO GENERAL
	// ════════════════════════════════════════════════════════════════

	private static final String CONSENT_GENERAL_HTML = """
			<div class="header">
			    <div class="clinic-name">{{tenant.displayName}}</div>
			    <div class="small">Bio Armonización Panamá</div>
			</div>

			<h1>Consentimiento Informado</h1>

			<p>
			    Yo, <strong>{{patient.fullName}}</strong>, con cédula/pasaporte
			    N.º <strong>{{patient.documentNumber}}</strong>, declaro que he sido informado(a)
			    de forma clara, suficiente, veraz y comprensible sobre los procedimientos
			    médico-estéticos que realizaré en esta clínica, incluyendo —sin limitarse a—
			    los siguientes:
			</p>

			<h3>Listado de procedimientos:</h3>
			<p>_______________________________________________________________________</p>
			<p>_______________________________________________________________________</p>

			<p>
			    Comprendo que los procedimientos médicos son electivos y no vitales, buscan
			    fines estéticos y/o funcionales, producen resultados temporales, variables e
			    individuales, no constituyen una garantía de rejuvenecimiento ni perfección estética.
			</p>

			<p>
			    Se me explicó: objetivo del tratamiento y su alcance razonable, alternativas
			    terapéuticas, incluyendo la abstención, límites anatómicos/biológicos propios
			    de cada paciente, que la medicina estética no es una ciencia exacta ni admite
			    resultados idénticos en todos los casos.
			</p>

			<p>
			    Reconozco que, como todo acto médico, existen <strong>riesgos previsibles e
			    imprevisibles</strong>, entre ellos sin carácter limitativo: dolor, edema,
			    inflamación, eritema, hematomas, equimosis, asimetrías, irregularidades,
			    cefalea, hipersensibilidad, infección local, reacciones inflamatorias o
			    alérgicas, resultados no deseados o transitorios.
			</p>

			<h2>Riesgos adicionales por procedimiento</h2>
			<ul>
			    <li><strong>Toxina botulínica:</strong> debilidad muscular transitoria, caída
			        parcial de párpados, asimetrías, efecto limitado o menor del esperado.</li>
			    <li><strong>Ácido hialurónico (rellenos / ojeras / labios / mentón /
			        rinomodelación):</strong> edema prolongado, nódulos, migración del producto,
			        Tyndall, oclusión vascular o complicaciones infrecuentes potencialmente graves.</li>
			    <li><strong>Radiesse® / hidroxiapatita cálcica:</strong> edema, reacción
			        inflamatoria, irregularidades o asimetrías.</li>
			    <li><strong>Sculptra® / bioestimuladores:</strong> nódulos tardíos,
			        sobre-estimulación tisular o resultados graduales no lineales.</li>
			    <li><strong>Enzimas / lipolíticos:</strong> dolor local, inflamación, asimetría,
			        fibrosis, resultado irregular o variable por zona.</li>
			    <li><strong>Dermapen / micropunción:</strong> irritación, descamación,
			        hiperpigmentación postinflamatoria si no siguen cuidados.</li>
			    <li><strong>Peeling químico:</strong> hiperpigmentación / hipopigmentación,
			        sensibilidad cutánea, quemadura superficial en casos excepcionales.</li>
			    <li><strong>Radiofrecuencia:</strong> enrojecimiento transitorio, sensación
			        térmica o edema leve.</li>
			    <li><strong>Limpieza facial y procedimientos cosméticos asociados:</strong>
			        irritación cutánea, reacción a productos tópicos.</li>
			</ul>

			<p>
			    Comprendo que algunas complicaciones pueden requerir tratamiento adicional,
			    medicación, procedimientos correctivos o seguimiento prolongado, los cuales
			    pueden no estar incluidos en el costo inicial.
			</p>

			<h2>Información veraz</h2>

			<p>
			    Declaro haber informado de manera completa y veraz sobre: enfermedades
			    actuales o previas, alergias conocidas o sospechadas, uso de medicamentos,
			    hormonas, anticoagulantes o suplementos, cirugías o procedimientos estéticos
			    previos, antecedentes de hiperpigmentación postinflamatoria, embarazo,
			    lactancia u otras condiciones especiales, productos de mi rutina facial,
			    cosméticos, ácidos, retinoides, peelings previos o procedimientos realizados
			    en otros centros.
			</p>

			<p>
			    Comprendo que la omisión o inexactitud en esta información aumenta riesgos
			    y puede afectar resultados. Me comprometo a:
			</p>
			<ul>
			    <li>seguir rigurosamente las indicaciones pre y post-procedimiento,</li>
			    <li>evitar productos o rutinas no autorizadas durante el período indicado,</li>
			    <li>comunicar oportunamente cualquier síntoma o evento inesperado,</li>
			    <li>asistir a mis controles programados.</li>
			</ul>

			<h2>Aceptación de resultados</h2>

			<p>Acepto que:</p>
			<ul>
			    <li>los resultados dependen de mi anatomía, biología, conducta y respuesta tisular,</li>
			    <li>no existe garantía expresa ni implícita de resultados estéticos específicos,</li>
			    <li>la percepción subjetiva del resultado no constituye por sí sola mala
			        práctica médica,</li>
			    <li>el equipo médico actuará conforme a criterio clínico y buena práctica
			        profesional.</li>
			</ul>

			<p>
			    Autorizo el registro y resguardo de mi información clínica conforme a la
			    normativa panameña aplicable y a la confidencialidad médico-paciente, con
			    uso exclusivo asistencial.
			</p>

			<h2>Autorización fotográfica</h2>

			<p>
			    Autorizo la toma de fotografías/vídeos con fines clínicos, comparativos y
			    académicos internos. (Seleccione una opción)
			</p>
			<div class="checkbox-option">☐ Autorizo su uso institucional informativo / educativo / publicitario</div>
			<div class="checkbox-option">☐ NO autorizo su uso publicitario</div>

			<p>
			    En caso de autorizar difusión: no genera derecho a compensación económica,
			    no tiene límite territorial ni temporal, se preservará mi dignidad e
			    identidad conforme a lo acordado.
			</p>

			<h2>Exoneración de responsabilidad</h2>

			<p>
			    Reconozco que el <strong>riesgo cero no existe</strong> en la práctica médica.
			    La clínica y su equipo no serán responsables por efectos derivados de:
			</p>
			<ul>
			    <li>factores biológicos o anatómicos propios del paciente,</li>
			    <li>variaciones individuales en la respuesta al tratamiento,</li>
			    <li>incumplimiento de indicaciones médicas,</li>
			    <li>uso paralelo de productos cosméticos o procedimientos externos no informados,</li>
			    <li>intervenciones posteriores realizadas en otros centros.</li>
			</ul>

			<h2>Declaración final</h2>

			<p>Declaro que:</p>
			<ul>
			    <li>he recibido información suficiente, clara y comprensible,</li>
			    <li>comprendo riesgos, beneficios, alternativas y límites del tratamiento,</li>
			    <li>no se me ha ofrecido garantía de resultado,</li>
			    <li>otorgo mi consentimiento libre, consciente y voluntario, sin presión o coacción.</li>
			</ul>

			<p>
			    Firmo este documento en ejercicio de mi autonomía y con plena confianza en
			    el equipo médico tratante.
			</p>

			<div class="signature-block">
			    <div class="signature-line">&#160;</div>
			    <div class="signature-label">Firma del paciente: {{patient.fullName}}</div>
			    <br/>
			    <div class="signature-label">Fecha: {{document.day}} / {{document.month}} / {{document.year}}</div>
			</div>

			<div class="footer-info">
			    Documento generado el {{document.date}} · {{tenant.displayName}}
			</div>
			""";

	// ════════════════════════════════════════════════════════════════
	// TEMPLATE: CONSENTIMIENTO TIRZEPATIDA + VITAMINA B12
	// ════════════════════════════════════════════════════════════════

	private static final String CONSENT_TIRZEPATIDA_HTML = """
			<div class="header">
			    <div class="clinic-name">{{tenant.displayName}}</div>
			    <div class="small">Programa Médico de Regulación del Apetito y Peso Corporal</div>
			</div>

			<h1>Consentimiento Informado</h1>
			<h2 style="text-align: center; border: none;">Aplicación de Tirzepatida + Vitamina B12</h2>

			<p>
			    Yo, <strong>{{patient.fullName}}</strong>, mayor de edad, con documento de
			    identidad N.º <strong>{{patient.documentNumber}}</strong>, manifiesto que he
			    sido informado(a) de manera clara, suficiente y comprensible por el médico
			    tratante sobre el tratamiento con tirzepatida asociada a vitamina B12, así
			    como de sus beneficios, riesgos, alternativas y posibles complicaciones.
			</p>

			<h2>¿En qué consiste el tratamiento?</h2>
			<p>
			    Se me ha explicado que el tratamiento consiste en la aplicación subcutánea
			    semanal de tirzepatida, asociada a vitamina B12, como parte de un programa
			    médico integral para el control de peso y optimización metabólica, que incluye
			    valoración clínica, exámenes de laboratorio, seguimiento médico periódico y
			    terapias complementarias cuando corresponda.
			</p>

			<h3>Objetivo del tratamiento</h3>
			<ul>
			    <li>mejorar el control del apetito,</li>
			    <li>favorecer la pérdida de peso y de grasa corporal,</li>
			    <li>mejorar la adherencia al plan nutricional,</li>
			    <li>apoyar el control metabólico de forma progresiva.</li>
			</ul>
			<p>
			    Se me ha explicado que <strong>no se garantiza un resultado específico</strong>,
			    ya que la respuesta depende de múltiples factores individuales.
			</p>

			<h3>Información sobre la tirzepatida</h3>
			<p>
			    Se me ha informado que la tirzepatida es un medicamento que actúa sobre los
			    receptores relacionados con la regulación del apetito y la glucosa, y que su
			    uso requiere titulación progresiva, control médico y vigilancia clínica estrecha.
			</p>

			<h3>Información sobre la vitamina B12</h3>
			<p>
			    Se me ha explicado que la vitamina B12 se utiliza como complemento del programa
			    médico, con fines de soporte metabólico y neurológico, y que no sustituye al
			    tratamiento principal ni a las modificaciones de estilo de vida.
			</p>

			<h2>Beneficios esperados</h2>
			<p>Entiendo que los posibles beneficios incluyen, entre otros:</p>
			<ul>
			    <li>reducción del apetito,</li>
			    <li>mayor sensación de saciedad,</li>
			    <li>disminución progresiva de peso y medidas,</li>
			    <li>mejor adherencia al tratamiento.</li>
			</ul>

			<h2>Riesgos y efectos adversos posibles</h2>
			<p>
			    Se me ha informado que, como todo tratamiento médico, la aplicación de tirzepatida
			    con vitamina B12 puede presentar efectos adversos, entre los cuales se incluyen,
			    pero no se limitan a:
			</p>

			<h3>Efectos frecuentes</h3>
			<ul>
			    <li>náuseas</li>
			    <li>vómitos</li>
			    <li>sensación de llenura precoz</li>
			    <li>distensión abdominal</li>
			    <li>estreñimiento o diarrea</li>
			    <li>reflujo o acidez</li>
			    <li>disminución marcada del apetito</li>
			</ul>

			<h3>Efectos menos frecuentes</h3>
			<ul>
			    <li>mareos</li>
			    <li>fatiga</li>
			    <li>dolor de cabeza</li>
			    <li>reacciones locales en el sitio de aplicación (dolor, enrojecimiento, hematoma)</li>
			</ul>

			<h3>Efectos poco frecuentes pero potencialmente graves</h3>
			<ul>
			    <li>deshidratación secundaria a vómitos persistentes</li>
			    <li>alteraciones gastrointestinales severas</li>
			    <li>empeoramiento de trastornos de vaciamiento gástrico</li>
			    <li>hipoglucemia, especialmente si utilizo otros medicamentos para diabetes</li>
			    <li>elevación de enzimas pancreáticas</li>
			    <li>pancreatitis aguda</li>
			    <li>alteraciones en la función renal secundarias a deshidratación</li>
			    <li>reacciones alérgicas</li>
			</ul>

			<h2>Riesgos específicos relevantes</h2>
			<p>Se me ha explicado de forma expresa que este tratamiento:</p>
			<ul>
			    <li><strong>NO debe utilizarse en embarazo ni lactancia.</strong></li>
			    <li>Está contraindicado en pacientes con:
			        <ul>
			            <li>antecedente personal o familiar de carcinoma medular de tiroides,</li>
			            <li>síndrome de neoplasia endocrina múltiple tipo 2 (MEN 2),</li>
			            <li>pancreatitis activa o recurrente,</li>
			            <li>gastroparesia severa.</li>
			        </ul>
			    </li>
			</ul>

			<h2>Advertencias importantes</h2>
			<p>
			    He sido informado(a) de que debo <strong>suspender la aplicación y contactar
			    de inmediato a la clínica</strong> si presento:
			</p>
			<ul>
			    <li>dolor abdominal intenso y persistente, especialmente si se irradia a la espalda,</li>
			    <li>vómitos continuos o incapacidad para ingerir líquidos,</li>
			    <li>mareos importantes o desmayos,</li>
			    <li>signos de reacción alérgica (ronchas, dificultad respiratoria, inflamación facial),</li>
			    <li>palpitaciones persistentes o malestar general importante.</li>
			</ul>

			<h2>Limitaciones y ausencia de garantía</h2>
			<p>Entiendo y acepto que:</p>
			<ul>
			    <li>este tratamiento no sustituye una alimentación saludable, ejercicio físico,
			        sueño adecuado ni control del estrés;</li>
			    <li>los resultados varían de un paciente a otro;</li>
			    <li>no se garantiza pérdida de peso específica ni porcentaje determinado de
			        reducción corporal.</li>
			</ul>

			<h2>Alternativas terapéuticas</h2>
			<p>Se me ha informado que existen otras alternativas para el manejo del peso corporal, entre ellas:</p>
			<ul>
			    <li>modificación intensiva del estilo de vida,</li>
			    <li>planes nutricionales supervisados,</li>
			    <li>otros medicamentos,</li>
			    <li>programas de actividad física,</li>
			    <li>tratamientos médicos distintos.</li>
			</ul>
			<p>He tenido la oportunidad de hacer preguntas y estas han sido respondidas de forma satisfactoria.</p>

			<h2>Declaración de información veraz</h2>
			<p>Declaro que he informado de manera completa y veraz al médico tratante sobre:</p>
			<ul>
			    <li>mis antecedentes personales y familiares,</li>
			    <li>mis enfermedades actuales,</li>
			    <li>los medicamentos que utilizo,</li>
			    <li>suplementos o terapias previas.</li>
			</ul>
			<p>Entiendo que la omisión de información relevante puede aumentar el riesgo de complicaciones.</p>

			<h2>Autorización para la aplicación</h2>
			<p>Por medio del presente documento:</p>
			<ul>
			    <li>✔ autorizo de forma libre, voluntaria e informada al médico tratante y al
			        personal de salud de {{tenant.displayName}} a realizar la aplicación
			        subcutánea de tirzepatida asociada a vitamina B12, así como los controles
			        clínicos necesarios.</li>
			    <li>✔ autorizo el manejo de eventuales efectos adversos conforme a criterio médico.</li>
			</ul>

			<h2>Exoneración de responsabilidad por resultados</h2>
			<p>Declaro expresamente que comprendo que:</p>
			<ul>
			    <li>el personal médico y asistencial no puede garantizar resultados estéticos,
			        metabólicos ni de pérdida de peso,</li>
			    <li>mi respuesta al tratamiento depende de factores individuales, biológicos
			        y conductuales.</li>
			</ul>

			<h2>Confidencialidad</h2>
			<p>
			    Autorizo el uso y resguardo de mi información clínica conforme a las normas
			    de confidencialidad médica vigentes.
			</p>

			<h2>Aceptación final</h2>
			<p>
			    Habiendo comprendido la información brindada, sus riesgos, beneficios,
			    alternativas y limitaciones, firmo el presente consentimiento informado de
			    forma libre y voluntaria.
			</p>

			<div class="signature-block">
			    <div class="signature-label">Nombre del paciente: <strong>{{patient.fullName}}</strong></div>
			    <br/>
			    <div class="signature-line">&#160;</div>
			    <div class="signature-label">Firma del paciente</div>
			    <br/>
			    <div class="signature-label">Fecha: {{document.day}} / {{document.month}} / {{document.year}}</div>
			</div>

			<div class="footer-info">
			    Documento generado el {{document.date}} · {{tenant.displayName}}
			</div>
			""";
}
