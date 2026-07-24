package com.bisioneers.medica.imports.domain;

/** Estado del lote de importación. */
public enum ImportBatchStatus {
	ANALYZING,   // subiendo/parseando
	ANALYZED,    // validado, esperando confirmación (NO escribió pacientes)
	COMMITTING,  // insertando pacientes
	COMMITTED,   // pacientes creados
	REVERTED,    // lote deshecho (pacientes desactivados)
	FAILED       // error irrecuperable durante analyze/commit
}