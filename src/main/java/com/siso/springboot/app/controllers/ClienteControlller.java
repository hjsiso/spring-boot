package com.siso.springboot.app.controllers;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.siso.springboot.app.models.entity.Cliente;
import com.siso.springboot.app.services.IClienteService;
import com.siso.springboot.app.util.paginator.PageRender;

@Controller
@SessionAttributes("cliente")
public class ClienteControlller {

	@Autowired
	private IClienteService clienteService;

	private final Logger log = LoggerFactory.getLogger(getClass());
	
	
	private final static String UPLOADS_FOLDER = "uploads";
	

	@GetMapping(value = "/uploads/{filename:.+}")
	public ResponseEntity<Resource> verForo(@PathVariable String filename) {
		Path pathFoto = Paths.get("uploads").resolve(filename).toAbsolutePath();
		log.info("pathUploads: " + Paths.get(UPLOADS_FOLDER).toAbsolutePath());

		log.info("pathFoto: " + pathFoto);
		Resource recurso = null;

		try {
			recurso = new UrlResource(pathFoto.toUri());
			log.info("recurso: " + recurso);

			if (!recurso.exists() || !recurso.isReadable()) {
				throw new RuntimeException("Error: no se puede cargar la imagen :" + pathFoto.toString());
			}

		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + recurso.getFilename() + "\"")
				.body(recurso);
	}

	@GetMapping(value = "/ver/{id}")
	public String ver(@PathVariable(value = "id") Long id, Map<String, Object> model, RedirectAttributes flash) {
		Cliente cliente = clienteService.findOne(id);
		if (cliente == null) {
			flash.addAttribute("error", "El cliente no existe en la bd");
			return "redirect:/listar";
		}

		model.put("cliente", cliente);
		model.put("titulo", "Detalle de cliente " + cliente.getNombre());
		return "ver";

	}

	@RequestMapping(value = "/listar", method = RequestMethod.GET)
	public String listar(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {

		Pageable pageRequest = new PageRequest(page, 5);

		Page<Cliente> clientes = clienteService.findAll(pageRequest);

		PageRender<Cliente> pageRender = new PageRender<Cliente>("/listar", clientes);

		model.addAttribute("titulo", "Listado de clientes");
		model.addAttribute("clientes", clientes);
		model.addAttribute("page", pageRender);

		return "listar";
	}

	@RequestMapping(value = "/form")
	public String crear(Map<String, Object> model) {
		Cliente cliente = new Cliente();
		model.put("cliente", cliente);
		model.put("titulo", "Formulario de Clientes");
		return "form";
	}

	@RequestMapping(value = "/form/{id}")
	public String editar(@PathVariable(value = "id") Long id, Map<String, Object> model, RedirectAttributes flash) {
		Cliente cliente = null;

		if (id > 0) {
			cliente = clienteService.findOne(id);
			if (cliente == null) {
				flash.addFlashAttribute("error", "Id de Cliente no existe.");
				return "redirect:/listar";
			}
		} else {
			flash.addFlashAttribute("error", "Id de Cliente no válido.");
			return "redirect:/listar";
		}
		model.put("cliente", cliente);
		model.put("titulo", "Editar Clientes");
		return "form";
	}

	@RequestMapping(value = "/form", method = RequestMethod.POST)
	public String guardar(@Valid Cliente cliente, BindingResult result, Model model,
			@RequestParam("file") MultipartFile foto, RedirectAttributes flash, SessionStatus status) {

		model.addAttribute("titulo", "Formulario de Clientes");
		if (result.hasErrors()) {
			return "form";
		}

		if (!foto.isEmpty()) {

			if (cliente.getId() != null && cliente.getId() > 0 && cliente.getFoto() != null
					&& cliente.getFoto().length() > 0) {

				String filename = cliente.getFoto();

				Path rootPath = Paths.get(UPLOADS_FOLDER).resolve(filename);
				Path rootAbsolutPath = rootPath.toAbsolutePath();

				File archivo = rootAbsolutPath.toFile();

				if (archivo.exists() && archivo.canRead()) {
					archivo.delete();
				}

			}

			String uniqueFilename = UUID.randomUUID().toString() + "_" + foto.getOriginalFilename();

			Path rootPath = Paths.get(UPLOADS_FOLDER).resolve(uniqueFilename);

			Path rootAbsolutPath = rootPath.toAbsolutePath();

			log.info("rootPath: " + rootPath);
			log.info("rootAbsolutPath: " + rootAbsolutPath);

			try {

				Files.copy(foto.getInputStream(), rootAbsolutPath);

				flash.addFlashAttribute("info", "Has subido correctamente '" + uniqueFilename + "'");

				cliente.setFoto(uniqueFilename);

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		String mensajeFlash = (cliente.getId() != null) ? "Cliente editado con exito" : "Cliente creado con exito";

		clienteService.save(cliente);
		status.setComplete();
		flash.addFlashAttribute("success", mensajeFlash);
		return "redirect:listar";

	}

	@RequestMapping(value = "/eliminar/{id}")
	public String eliminar(@PathVariable(value = "id") Long id, RedirectAttributes flash) {

		if (id > 0) {

			Cliente cliente = clienteService.findOne(id);

			if (cliente.getFoto() != null && cliente.getFoto().length() > 0) {

				String filename = cliente.getFoto();

				Path rootPath = Paths.get(UPLOADS_FOLDER).resolve(filename);
				Path rootAbsolutPath = rootPath.toAbsolutePath();

				File foto = rootAbsolutPath.toFile();

				if (foto.exists() && foto.canRead()) {
					if (foto.delete()) {
						flash.addFlashAttribute("info", "Foto " + cliente.getFoto() + " eliminada con exito.");
					}
				}

			}

			clienteService.delete(id);
			flash.addFlashAttribute("success", "Cliente eliminado con exito.");
		}
		return "redirect:/listar";
	}

}
